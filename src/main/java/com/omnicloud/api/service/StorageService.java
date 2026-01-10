package com.omnicloud.api.service;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.model.StorageProvider;
import com.omnicloud.api.repository.StorageProviderRepository;
import io.minio.*;
import org.springframework.stereotype.Service;
import com.omnicloud.api.controller.PolicyController;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class StorageService {

    private final StorageProviderRepository providerRepository;

    public StorageService(StorageProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public void uploadShards(List<Shard> shards, String fileId) {
        List<StorageProvider> providers = providerRepository.findAll();

        if (providers.isEmpty()) {
            throw new RuntimeException("No storage providers registered! Please add a Cloud Provider via /api/v1/providers");
        }

        // 1. Yasaklı Bölgeleri Çekiyoruz
        List<String> blockedRegions = (List<String>) PolicyController.currentPolicy.getOrDefault("blocked_regions", new ArrayList<>());
        System.out.println("DEBUG: Blocked Regions: " + blockedRegions); // Konsolda bunu görmelisin

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Shard shard : shards) {
            int providerIndex = shard.getIndex() % providers.size();
            StorageProvider initialProvider = providers.get(providerIndex);

            StorageProvider finalProvider;

            // 2. KONTROL BURADA YAPILIYOR
            if (blockedRegions.contains(initialProvider.getRegion())) {
                System.out.println("🛡️ POLICY ALERT: Region '" + initialProvider.getRegion() + "' is BLOCKED!");

                // Yasaklı olmayan ilk sunucuyu bul
                finalProvider = providers.stream()
                        .filter(p -> !blockedRegions.contains(p.getRegion()))
                        .findFirst()
                        .orElse(initialProvider);

                System.out.println("↪️ Redirecting Shard " + shard.getIndex() + " to: " + finalProvider.getName());
            } else {
                finalProvider = initialProvider;
            }

            // Yükleme işlemi (finalProvider ile)
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    MinioClient client = getClientForProvider(finalProvider);
                    String objectName = fileId + "/" + shard.getIndex();
                    byte[] data = shard.getData();

                    boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(finalProvider.getBucketName()).build());
                    if (!found) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(finalProvider.getBucketName()).build());
                    }

                    client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(finalProvider.getBucketName())
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(data), data.length, -1)
                                    .build()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public byte[] downloadShard(String bucketName, String objectName, int shardIndex) {
        List<StorageProvider> providers = providerRepository.findAll();
        if (providers.isEmpty()) return null;

        // Re-calculate which provider *should* have this shard
        int providerIndex = shardIndex % providers.size();
        StorageProvider targetProvider = providers.get(providerIndex);

        try {
            MinioClient client = getClientForProvider(targetProvider);
            try (InputStream stream = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(targetProvider.getBucketName()) // Use provider's specific bucket name
                            .object(objectName)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            System.err.println("⚠️ WARNING: Could not fetch shard " + shardIndex + " from " + targetProvider.getName());
            return null; // Return null so Erasure Service knows to reconstruct
        }
    }

    public void deleteShard(String bucketName, String objectName, int shardIndex) {
        List<StorageProvider> providers = providerRepository.findAll();
        if (providers.isEmpty()) return;

        int providerIndex = shardIndex % providers.size();
        StorageProvider targetProvider = providers.get(providerIndex);

        try {
            MinioClient client = getClientForProvider(targetProvider);
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(targetProvider.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not delete shard from " + targetProvider.getName());
        }
    }

    public void uploadShardsToSpecificProvider(Shard shard, String fileId, StorageProvider provider) {
        try {
            MinioClient client = getClientForProvider(provider);
            String objectName = fileId + "/" + shard.getIndex();
            byte[] data = shard.getData();

            boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(provider.getBucketName()).build());
            if (!found) {
                client.makeBucket(MakeBucketArgs.builder().bucket(provider.getBucketName()).build());
            }

            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(provider.getBucketName())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to new provider: " + e.getMessage());
        }
    }

    public List<String> getSystemHealth() {
        List<String> healthReport = new ArrayList<>();
        List<StorageProvider> providers = providerRepository.findAll();

        if (providers.isEmpty()) {
            healthReport.add("CRITICAL: No Providers Registered.");
            return healthReport;
        }

        providers.forEach(provider -> {
            try {
                MinioClient client = getClientForProvider(provider);
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(provider.getBucketName()).build());
                if (exists) {
                    healthReport.add("✅ " + provider.getName() + " [" + provider.getType() + "]: ONLINE");
                } else {
                    healthReport.add("❌ " + provider.getName() + ": BUCKET MISSING");
                }
            } catch (Exception e) {
                healthReport.add("🔥 " + provider.getName() + ": CONNECTION FAILED (" + e.getMessage() + ")");
            }
        });
        return healthReport;
    }

    // --- Helper Method ---
    private MinioClient getClientForProvider(StorageProvider provider) {
        return MinioClient.builder()
                .endpoint(provider.getEndpointUrl())
                .credentials(provider.getAccessKey(), provider.getSecretKey())
                .region(provider.getRegion()) // Important for AWS/Azure
                .build();
    }
}