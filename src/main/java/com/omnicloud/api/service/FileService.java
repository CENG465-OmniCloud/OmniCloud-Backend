package com.omnicloud.api.service;

import com.omnicloud.api.model.*;
import com.omnicloud.api.repository.FileMetadataRepository;
import com.omnicloud.api.repository.StorageProviderRepository;
import com.omnicloud.api.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileMetadataRepository repository;
    private final EncryptionService encryptionService;
    private final ErasureService erasureService;
    private final StorageService storageService;
    private final StorageProviderRepository providerRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final PolicyService policyService;

    private static final int DATA_SHARDS = 4;

    public FileService(FileMetadataRepository repository,
                       EncryptionService encryptionService,
                       ErasureService erasureService,
                       StorageService storageService,
                       StorageProviderRepository providerRepository,
                       AuditService auditService,
                       UserRepository userRepository,
                       PolicyService policyService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.erasureService = erasureService;
        this.storageService = storageService;
        this.providerRepository = providerRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.policyService = policyService;
    }

    @Transactional
    public FileMetadata uploadFile(MultipartFile file) throws Exception {
        User currentUser = getCurrentUser();

        // 1. Fetch Providers
        List<StorageProvider> allProviders = providerRepository.findAll();
        if (allProviders.isEmpty()) {
            throw new RuntimeException("No Storage Providers found!");
        }

        // 2. Geo-Fencing: Filter Active Providers FIRST
        UserPolicy userPolicy = policyService.getMyPolicy();
        List<String> blockedRegions = userPolicy.getBlockedRegions();

        List<StorageProvider> activeProviders = allProviders.stream()
                .filter(p -> !blockedRegions.contains(p.getRegion()))
                .collect(Collectors.toList());

        if (activeProviders.isEmpty()) {
            throw new RuntimeException("UPLOAD FAILED: All providers are in your blocked regions!");
        }

        // 3. Generate Keys & Metadata
        SecretKey key = encryptionService.generateKey();
        IvParameterSpec iv = encryptionService.generateIv();

        FileMetadata metadata = FileMetadata.builder()
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .uploadDate(LocalDateTime.now())
                .encryptionKey(encryptionService.keyToString(key))
                .iv(Base64.getEncoder().encodeToString(iv.getIV()))
                .owner(currentUser)
                .build();

        metadata = repository.save(metadata);
        String fileId = metadata.getId().toString();

        // 4. Encrypt & Split
        byte[] originalBytes = file.getBytes();
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes, key, iv);
        List<Shard> shards = erasureService.encode(encryptedBytes);

        // 5. Upload (Pass the FILTERED list)
        // This fixes the compilation error and logic bug
        storageService.uploadShards(shards, fileId, activeProviders);

        // 6. Save Shard Metadata
        for (Shard s : shards) {
            // Calculate index using ACTIVE list size
            int providerIndex = s.getIndex() % activeProviders.size();
            StorageProvider usedProvider = activeProviders.get(providerIndex);

            ShardMetadata sm = ShardMetadata.builder()
                    .shardIndex(s.getIndex())
                    .minioBucketName(usedProvider.getBucketName())
                    .status(ShardStatus.ALIVE)
                    .build();
            metadata.addShard(sm);
        }

        auditService.log("FILE_UPLOAD", currentUser.getUsername(),
                "File '" + file.getOriginalFilename() + "' uploaded successfully.", "SUCCESS");

        return repository.save(metadata);
    }

    public byte[] downloadFile(UUID fileId) throws Exception {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Security Check
        User currentUser = getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN && !metadata.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("ACCESS DENIED: You do not own this file.");
        }

        List<CompletableFuture<Shard>> futures = new ArrayList<>();

        for (ShardMetadata sm : metadata.getShards()) {
            CompletableFuture<Shard> future = CompletableFuture.supplyAsync(() -> {
                String objectName = fileId.toString() + "/" + sm.getShardIndex();
                // Fix: Use bucket name lookup from StorageService
                byte[] data = storageService.downloadShard(sm.getMinioBucketName(), objectName, sm.getShardIndex());
                if (data == null) return null;
                return new Shard(sm.getShardIndex(), data);
            });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<Shard> downloadedShards = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (downloadedShards.size() < DATA_SHARDS) {
            throw new RuntimeException("CRITICAL DATA LOSS: Only retrieved " + downloadedShards.size() + "/6 shards.");
        }

        int totalEncryptedSize = downloadedShards.get(0).getData().length * DATA_SHARDS;
        byte[] recoveredEncrypted = erasureService.decode(downloadedShards, totalEncryptedSize);

        SecretKey key = encryptionService.stringToKey(metadata.getEncryptionKey());
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(metadata.getIv()));

        return encryptionService.decrypt(recoveredEncrypted, key, iv);
    }

    @Transactional
    public String repairFile(UUID fileId) throws Exception {
        System.out.println("🔧 Starting Repair for File: " + fileId);

        // 1. Recover File
        // Note: downloadFile performs the security check internally, so we are safe.
        byte[] recoveredFile = downloadFile(fileId);

        FileMetadata metadata = repository.findById(fileId).orElseThrow();
        SecretKey key = encryptionService.stringToKey(metadata.getEncryptionKey());
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(metadata.getIv()));

        // 2. Re-Encrypt & Split
        byte[] encryptedBytes = encryptionService.encrypt(recoveredFile, key, iv);
        List<Shard> allShards = erasureService.encode(encryptedBytes);

        List<StorageProvider> allProviders = providerRepository.findAll();
        int repairedCount = 0;
        boolean metadataUpdated = false;

        for (Shard shard : allShards) {
            ShardMetadata shardMeta = metadata.getShards().get(shard.getIndex());
            String currentBucket = shardMeta.getMinioBucketName();

            // Check existence
            byte[] existingData = storageService.downloadShard(
                    currentBucket,
                    fileId.toString() + "/" + shard.getIndex(),
                    shard.getIndex()
            );

            if (existingData == null) {
                System.out.println("⚠️ Missing Shard: " + shard.getIndex());

                // Find original provider
                StorageProvider targetProvider = allProviders.stream()
                        .filter(p -> p.getBucketName().equals(currentBucket))
                        .findFirst()
                        .orElse(null);

                boolean uploadSuccess = false;

                // Try original provider first
                if (targetProvider != null) {
                    try {
                        // Fix: Use specific upload method
                        storageService.uploadShardsToSpecificProvider(shard, fileId.toString(), targetProvider);
                        uploadSuccess = true;
                        System.out.println("✅ Restored to ORIGINAL: " + targetProvider.getName());
                    } catch (Exception e) {
                        System.out.println("❌ Original provider DEAD.");
                    }
                }

                // Failover to new provider
                if (!uploadSuccess) {
                    List<String> usedBuckets = metadata.getShards().stream()
                            .map(ShardMetadata::getMinioBucketName)
                            .toList();

                    StorageProvider spareProvider = allProviders.stream()
                            .filter(p -> !usedBuckets.contains(p.getBucketName()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No spare providers!"));

                    storageService.uploadShardsToSpecificProvider(shard, fileId.toString(), spareProvider);

                    // Update DB
                    shardMeta.setMinioBucketName(spareProvider.getBucketName());
                    metadataUpdated = true;
                    repairedCount++;

                    auditService.log("FAILOVER", "system",
                            "Shard " + shard.getIndex() + " moved to " + spareProvider.getName(), "WARNING");
                } else {
                    repairedCount++;
                }
            }
        }

        if (metadataUpdated) repository.save(metadata);

        return repairedCount > 0 ? "Restored " + repairedCount + " shards." : "System Healthy.";
    }

    @Transactional
    public void deleteFile(UUID fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // Fix: Add Security Check
        User currentUser = getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN && !metadata.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("ACCESS DENIED: You do not own this file.");
        }

        for (ShardMetadata shard : metadata.getShards()) {
            storageService.deleteShard(
                    shard.getMinioBucketName(),
                    fileId.toString() + "/" + shard.getShardIndex(),
                    shard.getShardIndex()
            );
        }

        repository.delete(metadata);
        auditService.log("FILE_DELETE", currentUser.getUsername(), "Deleted: " + metadata.getFilename(), "WARNING");
    }

    public List<FileMetadata> listFiles() {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return repository.findAll();
        }
        return repository.findAllByOwner(currentUser);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found in context"));
    }
}