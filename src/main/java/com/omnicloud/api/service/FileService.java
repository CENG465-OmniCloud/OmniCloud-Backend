package com.omnicloud.api.service;

import com.omnicloud.api.config.MinioConfig;
import com.omnicloud.api.model.*;
import com.omnicloud.api.repository.FileMetadataRepository;
import com.omnicloud.api.repository.StorageProviderRepository;
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
    private final StorageProviderRepository providerRepository;
    private final AuditService auditService;

    // Sabit 6 Node, 4 Data, 2 Parity
    private static final int DATA_SHARDS = 4;

    public FileService(FileMetadataRepository repository,
                       EncryptionService encryptionService,
                       ErasureService erasureService,
                       StorageService storageService,
                       StorageProviderRepository providerRepository,
                       AuditService auditService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.erasureService = erasureService;
        this.storageService = storageService;
        this.providerRepository = providerRepository;
        this.auditService = auditService;
    }

    @Transactional
    public FileMetadata uploadFile(MultipartFile file) throws Exception {
        // 0. Fetch Providers
        List<StorageProvider> providers = providerRepository.findAll();
        if (providers.isEmpty()) {
            throw new RuntimeException("No Storage Providers found! Cannot upload file.");
        }

        // --- YENİ: Yasaklı Bölgeleri Çek ---
        List<String> blockedRegions = (List<String>) com.omnicloud.api.controller.PolicyController.currentPolicy.getOrDefault("blocked_regions", new ArrayList<>());

        // 1. Generate Security Keys
        SecretKey key = encryptionService.generateKey();
        IvParameterSpec iv = encryptionService.generateIv();

        // ADIM A: Metadata nesnesini oluştur
        FileMetadata metadata = FileMetadata.builder()
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .uploadDate(LocalDateTime.now())
                .encryptionKey(encryptionService.keyToString(key))
                .iv(Base64.getEncoder().encodeToString(iv.getIV()))
                .build();

        // ADIM B: İlk Kayıt
        metadata = repository.save(metadata);
        String fileId = metadata.getId().toString();

        // 2. Read & Encrypt
        byte[] originalBytes = file.getBytes();
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes, key, iv);

        // 3. Split
        List<Shard> shards = erasureService.encode(encryptedBytes);

        // 4. Upload (StorageService fiziksel yüklemeyi ve yönlendirmeyi yapar)
        storageService.uploadShards(shards, fileId);

        // 5. Shard Metadata'larını ekle
        for (Shard s : shards) {
            // Hangi sağlayıcıya gitmesi gerekiyordu?
            int providerIndex = s.getIndex() % providers.size();
            StorageProvider targetProvider = providers.get(providerIndex);

            // --- YENİ: METADATA DÜZELTME (JSON Çıktısı İçin) ---
            // Eğer hedef yasaklıysa, StorageService zaten onu güvenli yere attı.
            // Biz de veritabanına "Bu parça aslında şuraya gitti" diye doğrusunu yazmalıyız.
            if (blockedRegions.contains(targetProvider.getRegion())) {

                // LOG: Yasaklı bölge uyarısı
                auditService.log("GEO_FENCE_REDIRECT", "system",
                        "Shard " + s.getIndex() + " redirected from blocked region: " + targetProvider.getRegion(),
                        "WARNING");

                // Güvenli sağlayıcıyı bul (StorageService ile aynı mantık)
                targetProvider = providers.stream()
                        .filter(p -> !blockedRegions.contains(p.getRegion()))
                        .findFirst()
                        .orElse(targetProvider);
            }
            // ----------------------------------------------------

            ShardMetadata sm = ShardMetadata.builder()
                    .shardIndex(s.getIndex())
                    .minioBucketName(targetProvider.getBucketName()) // Artık doğru bucket ismini yazıyor
                    .status(ShardStatus.ALIVE)
                    .build();
            metadata.addShard(sm);
        }

        // --- YENİ: BAŞARILI YÜKLEME LOGU ---
        auditService.log("FILE_UPLOAD", "user_admin",
                "File '" + file.getOriginalFilename() + "' uploaded successfully.",
                "SUCCESS");

        // ADIM C: İkinci Kayıt
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
    public String repairFile(UUID fileId) throws Exception {
        System.out.println("🔧 Starting Smart Repair Process for File ID: " + fileId);
        auditService.log("MAINTENANCE_START", "system", "Repair process started for file ID: " + fileId, "INFO");

        // 1. Dosyayı kurtar (Reconstruct)
        byte[] recoveredFile = downloadFile(fileId);
        if (recoveredFile == null) {
            throw new RuntimeException("File recovery failed. Not enough shards available.");
        }

        // 2. Metadata ve Key'leri al
        FileMetadata metadata = repository.findById(fileId).orElseThrow();
        SecretKey key = encryptionService.stringToKey(metadata.getEncryptionKey());
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(metadata.getIv()));

        // 3. Dosyayı tekrar şifrele ve parçala
        byte[] encryptedBytes = encryptionService.encrypt(recoveredFile, key, iv);
        List<Shard> allShards = erasureService.encode(encryptedBytes);

        // 4. Tüm aktif sağlayıcıları çek (Yeni eklediğin dahil!)
        List<StorageProvider> allProviders = providerRepository.findAll();

        int repairedCount = 0;
        boolean metadataUpdated = false; // DB güncellemesi gerekecek mi?

        // 5. Her parça için kontrol et
        for (Shard shard : allShards) {
            // DB'den bu parçanın şu an nerede olması gerektiğini öğren
            ShardMetadata shardMeta = metadata.getShards().get(shard.getIndex());
            String currentBucket = shardMeta.getMinioBucketName();

            // Parça yerinde duruyor mu?
            byte[] existingData = storageService.downloadShard(
                    currentBucket,
                    fileId.toString() + "/" + shard.getIndex(),
                    shard.getIndex()
            );

            // EĞER PARÇA EKSİKSE (veya sunucu çökmüşse null döner)
            if (existingData == null) {
                System.out.println("⚠️ Missing Shard detected: Index " + shard.getIndex());

                // Hedef sunucuyu bul (İsminden bucket'ı buluyoruz)
                StorageProvider targetProvider = allProviders.stream()
                        .filter(p -> p.getBucketName().equals(currentBucket))
                        .findFirst()
                        .orElse(null);

                boolean uploadSuccess = false;

                // SENARYO A: Eski sunucu hala listede var, bir deneyelim (Belki sadece dosya silinmiştir, sunucu sağlamdır)
                if (targetProvider != null) {
                    try {
                        List<Shard> singleShard = new ArrayList<>();
                        singleShard.add(shard);
                        storageService.uploadShards(singleShard, fileId.toString()); // Sadece bu metot bucket kontrolü yapıyor
                        uploadSuccess = true;
                        System.out.println("✅ Restored to ORIGINAL provider: " + targetProvider.getName());
                    } catch (Exception e) {
                        System.out.println("❌ Original provider is DEAD. Looking for a new home...");
                    }
                }

                // SENARYO B: Eski sunucu ölmüş veya upload başarısız olmuş. YENİ SAĞLAYICI ARA!
                if (!uploadSuccess) {
                    // Bu dosyanın parçalarının ZATEN yüklü olduğu bucket'ları listele (Çakışma olmasın)
                    List<String> usedBuckets = metadata.getShards().stream()
                            .map(ShardMetadata::getMinioBucketName)
                            .toList();

                    // Hiç kullanılmayan, boşta bekleyen bir sağlayıcı bul (Yedek Oyuncu)
                    StorageProvider spareProvider = allProviders.stream()
                            .filter(p -> !usedBuckets.contains(p.getBucketName())) // Bu dosyadan hiç parça almamış olsun
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("CRITICAL: No spare providers available to migrate shard!"));

                    // Yeni sağlayıcıya yükle
                    // Manuel yükleme yapıyoruz çünkü uploadShards metodu metadata güncellemez
                    storageService.uploadShardsToSpecificProvider(shard, fileId.toString(), spareProvider);

                    // --- KRİTİK: DB'DEKİ ADRESİ GÜNCELLE ---
                    shardMeta.setMinioBucketName(spareProvider.getBucketName()); // Artık yeni adresi bu!
                    metadataUpdated = true;

                    auditService.log("FAILOVER_REPAIR", "system",
                            "Shard " + shard.getIndex() + " migrated to NEW provider: " + spareProvider.getName(),
                            "WARNING");

                    System.out.println("♻️ MIGRATED Shard " + shard.getIndex() + " to " + spareProvider.getName());
                    repairedCount++;
                } else {
                    repairedCount++;
                }
            }
        }

        // Eğer adres değişikliği yaptıysak DB'ye kaydet
        if (metadataUpdated) {
            repository.save(metadata);
        }

        if (repairedCount > 0) {
            return "SUCCESS: Restored & Rebalanced " + repairedCount + " shards.";
        } else {
            return "System Healthy: No shards were missing.";
        }
    }

    @Transactional
    public void deleteFile(UUID fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found with ID: " + fileId));

        // İsmi sakla (Silinmeden önce)
        String filename = metadata.getFilename();

        // 2. Fiziksel Silme
        for (ShardMetadata shard : metadata.getShards()) {
            String objectName = fileId.toString() + "/" + shard.getShardIndex();
            storageService.deleteShard(
                    shard.getMinioBucketName(),
                    objectName,
                    shard.getShardIndex()
            );
        }

        // 3. Veritabanından Silme
        repository.delete(metadata);

        // --- YENİ: SİLME LOGU ---
        auditService.log("FILE_DELETE", "admin_user",
                "File '" + filename + "' was permanently deleted.",
                "WARNING");

        System.out.println("🗑️ File " + fileId + " deleted.");
    }

    public List<FileMetadata> listFiles() {
        return repository.findAll();
    }
}
