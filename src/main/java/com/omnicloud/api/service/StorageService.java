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
     * Uploads shards to registered providers in PARALLEL.
     * Uses Round-Robin to distribute shards if there are fewer providers than shards.
     */
    public void uploadShards(List<Shard> shards, String fileId) {
        List<StorageProvider> providers = providerRepository.findAll();

        if (providers.isEmpty()) {
            throw new RuntimeException("No storage providers registered! Please add a Cloud Provider via /api/v1/providers");
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Shard shard : shards) {
            // Logic: Map Shard Index -> Provider Index
            // If you have 6 shards and 6 providers, it maps 1:1.
            // If you have 6 shards and 3 providers, it distributes 2 shards per provider.
            int providerIndex = shard.getIndex() % providers.size();
            StorageProvider targetProvider = providers.get(providerIndex);

            // Launch async upload task
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    MinioClient client = getClientForProvider(targetProvider);

                    String objectName = fileId + "/" + shard.getIndex();
                    byte[] data = shard.getData();

                    // Ensure bucket exists (Lazy check)
                    // In production, you might skip this for speed, but it's safe for now.
                    boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(targetProvider.getBucketName()).build());
                    if (!found) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(targetProvider.getBucketName()).build());
                    }

                    client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(targetProvider.getBucketName())
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(data), data.length, -1)
                                    .build()
                    );
                } catch (Exception e) {
                    System.err.println("❌ Failed to upload shard " + shard.getIndex() + " to " + targetProvider.getName() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for ALL uploads to finish
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