package com.omnicloud.api.service;

import com.omnicloud.api.config.MinioConfig;
import com.omnicloud.api.model.FileMetadata;
import com.omnicloud.api.model.Shard;
import com.omnicloud.api.model.ShardMetadata;
import com.omnicloud.api.model.ShardStatus;
import com.omnicloud.api.repository.FileMetadataRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileMetadataRepository repository;
    private final EncryptionService encryptionService;
    private final ErasureService erasureService;
    private final StorageService storageService;

    // Sabit 6 Node, 4 Data, 2 Parity
    private static final int DATA_SHARDS = 4;

    public FileService(FileMetadataRepository repository, EncryptionService encryptionService, ErasureService erasureService, StorageService storageService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.erasureService = erasureService;
        this.storageService = storageService;
    }

    @Transactional
    public FileMetadata uploadFile(MultipartFile file) throws Exception {
        // 1. Generate Security Keys
        SecretKey key = encryptionService.generateKey();
        IvParameterSpec iv = encryptionService.generateIv();

        // ADIM A: Önce Metadata nesnesini oluştur
        FileMetadata metadata = FileMetadata.builder()
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .uploadDate(LocalDateTime.now())
                .encryptionKey(encryptionService.keyToString(key))
                .iv(Base64.getEncoder().encodeToString(iv.getIV()))
                .build();

        // ADIM B: İlk Kayıt -> Bu işlem nesneye bir UUID atar.
        metadata = repository.save(metadata);
        String fileId = metadata.getId().toString();

        // 2. Read & Encrypt
        byte[] originalBytes = file.getBytes();
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes, key, iv);

        // 3. Split (Erasure Coding)
        List<Shard> shards = erasureService.encode(encryptedBytes);

        // 4. Upload to MinIO
        storageService.uploadShards(shards, fileId);

        // 5. Shard Metadata'larını ekle
        for (Shard s : shards) {
            ShardMetadata sm = ShardMetadata.builder()
                    .shardIndex(s.getIndex())
                    .minioBucketName(MinioConfig.COMMON_BUCKET_NAME)
                    .status(ShardStatus.ALIVE)
                    .build();
            metadata.addShard(sm);
        }

        // ADIM C: İkinci Kayıt -> Shard bilgileriyle beraber güncelliyoruz.
        return repository.save(metadata);
    }

    public byte[] downloadFile(UUID fileId) throws Exception {
        // 1. Fetch Metadata
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // 2. Parallel Fetch from MinIO
        List<CompletableFuture<Shard>> futures = new ArrayList<>();

        for (ShardMetadata sm : metadata.getShards()) {
            CompletableFuture<Shard> future = CompletableFuture.supplyAsync(() -> {
                String objectName = fileId.toString() + "/" + sm.getShardIndex();
                byte[] data = storageService.downloadShard(sm.getMinioBucketName(), objectName, sm.getShardIndex());

                if (data == null) return null; // Node was down
                return new Shard(sm.getShardIndex(), data);
            });
            futures.add(future);
        }

        // Wait for all attempts
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 3. Collect Valid Shards
        List<Shard> downloadedShards = futures.stream()
                .map(CompletableFuture::join)
                .filter(shard -> shard != null) // Filter out failed downloads
                .collect(Collectors.toList());

        if (downloadedShards.size() < DATA_SHARDS) {
            throw new RuntimeException("CRITICAL DATA LOSS: Only retrieved " + downloadedShards.size() + "/6 shards. Need 4.");
        }

        // 4. Reconstruct (Erasure Decode)
        // Therefore: shardSize * DATA_SHARDS gives us the exact encrypted size.
        int shardSize = downloadedShards.get(0).getData().length;
        int totalEncryptedSize = shardSize * DATA_SHARDS;

        byte[] recoveredEncrypted = erasureService.decode(downloadedShards, totalEncryptedSize);

        // 5. Decrypt
        SecretKey key = encryptionService.stringToKey(metadata.getEncryptionKey());
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(metadata.getIv()));

        byte[] decryptedData = encryptionService.decrypt(recoveredEncrypted, key, iv);

        // 6. Trim Padding (AES Padding might add bytes, Stream logic handles this usually)
        return decryptedData;
    }

    public List<FileMetadata> listFiles() {
        return repository.findAll();
    }
}
