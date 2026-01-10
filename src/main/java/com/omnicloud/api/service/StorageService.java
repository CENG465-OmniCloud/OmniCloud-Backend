package com.omnicloud.api.service;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.model.StorageProvider;
import com.omnicloud.api.repository.StorageProviderRepository;
import io.minio.*;
import org.springframework.stereotype.Service;

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

    /**
     * Uploads shards ONLY to the providers passed in 'activeProviders'.
     * This respects the Geo-Fencing logic from FileService.
     */
    public void uploadShards(List<Shard> shards, String fileId, List<StorageProvider> activeProviders) {
        if (activeProviders == null || activeProviders.isEmpty()) {
            throw new RuntimeException("No active storage providers available for upload!");
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Shard shard : shards) {
            // Distribute round-robin among the ACTIVE providers only
            int providerIndex = shard.getIndex() % activeProviders.size();
            StorageProvider targetProvider = activeProviders.get(providerIndex);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    uploadToProvider(targetProvider, fileId, shard);
                } catch (Exception e) {
                    throw new RuntimeException("Upload failed for shard " + shard.getIndex(), e);
                }
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Helper for single shard upload (Used by Repair & Failover)
     */
    public void uploadShardsToSpecificProvider(Shard shard, String fileId, StorageProvider provider) {
        try {
            uploadToProvider(provider, fileId, shard);
        } catch (Exception e) {
            throw new RuntimeException("Manual upload failed: " + e.getMessage());
        }
    }

    // Shared internal logic to avoid code duplication
    private void uploadToProvider(StorageProvider provider, String fileId, Shard shard) throws Exception {
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
    }

    /**
     * Finds the correct provider by looking up the BUCKET NAME.
     * This fixes bugs where shards moved during repair could not be found.
     */
    public byte[] downloadShard(String bucketName, String objectName, int shardIndex) {
        // Correct Logic: Find the provider that owns this bucket
        StorageProvider targetProvider = providerRepository.findByBucketName(bucketName)
                .orElse(null);

        // Fallback: If DB lookup fails (rare), try math as a last resort
        if (targetProvider == null) {
            List<StorageProvider> all = providerRepository.findAll();
            if (!all.isEmpty()) targetProvider = all.get(shardIndex % all.size());
        }

        if (targetProvider == null) return null;

        try {
            MinioClient client = getClientForProvider(targetProvider);
            try (InputStream stream = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Shard " + shardIndex + " missing from " + bucketName);
            return null;
        }
    }

    public void deleteShard(String bucketName, String objectName, int shardIndex) {
        StorageProvider targetProvider = providerRepository.findByBucketName(bucketName)
                .orElse(null);

        if (targetProvider == null) return;

        try {
            MinioClient client = getClientForProvider(targetProvider);
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            System.err.println("⚠️ Could not delete shard from " + bucketName);
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
                healthReport.add("🔥 " + provider.getName() + ": OFFLINE (" + e.getMessage() + ")");
            }
        });
        return healthReport;
    }

    private MinioClient getClientForProvider(StorageProvider provider) {
        return MinioClient.builder()
                .endpoint(provider.getEndpointUrl())
                .credentials(provider.getAccessKey(), provider.getSecretKey())
                .region(provider.getRegion())
                .build();
    }
}