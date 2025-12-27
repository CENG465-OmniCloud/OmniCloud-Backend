package com.omnicloud.api.service;

import com.omnicloud.api.config.MinioConfig;
import com.omnicloud.api.model.Shard;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class StorageService {

    private final Map<Integer, MinioClient> minioClients;

    public StorageService(Map<Integer, MinioClient> minioClients) {
        this.minioClients = minioClients;
        // Initialize buckets on startup (Optional, but good for safety)
        ensureBucketsExist();
    }

    /**
     * Uploads 6 shards to 6 different providers in PARALLEL.
     */
    public void uploadShards(List<Shard> shards, String fileId) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Shard shard : shards) {
            // Select the correct client for this shard index (0-5)
            MinioClient client = minioClients.get(shard.getIndex());

            // Launch async upload task
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String objectName = fileId + "/" + shard.getIndex(); // Folder structure: file-uuid/0
                    byte[] data = shard.getData();

                    client.putObject(
                            PutObjectArgs.builder()
                                    .bucket(MinioConfig.COMMON_BUCKET_NAME)
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(data), data.length, -1)
                                    .build()
                    );
                    // System.out.println("Shard " + shard.getIndex() + " uploaded.");
                } catch (Exception e) {
                    // For now, just print error. In Day 8, we handle this as a "Dead Node".
                    System.err.println("❌ Failed to upload shard " + shard.getIndex() + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for ALL uploads to finish before returning
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void ensureBucketsExist() {
        minioClients.forEach((index, client) -> {
            try {
                String bucket = MinioConfig.COMMON_BUCKET_NAME;
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    System.out.println("Created bucket on Cloud Node " + index);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not check bucket on Cloud Node " + index);
            }
        });
    }
}