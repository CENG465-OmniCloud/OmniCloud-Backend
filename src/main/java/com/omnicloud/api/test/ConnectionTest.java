package com.omnicloud.api.test;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class ConnectionTest {

    @Autowired
    private StorageService storageService;

    @Test
    public void testCloudUpload() {
        System.out.println("=== TEST: Connecting to MinIO Containers ===");

        // 1. Create fake shards
        List<Shard> dummyShards = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            byte[] data = ("Test Data for Cloud " + i).getBytes();
            dummyShards.add(new Shard(i, data));
        }

        // 2. Upload them
        String fileId = UUID.randomUUID().toString();
        storageService.uploadShards(dummyShards, fileId);

        System.out.println("✅ Successfully uploaded 6 shards to 6 containers!");
        System.out.println("Check MinIO Browser to see folder: " + fileId);
    }
}