package com.omnicloud.api.test;

import com.omnicloud.api.model.FileMetadata;
import com.omnicloud.api.model.ShardMetadata;
import com.omnicloud.api.model.ShardStatus;
import com.omnicloud.api.repository.FileMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // This starts the full Spring Context (and connects to DB)
public class MetadataTest {

    @Autowired
    private FileMetadataRepository repository;

    @Test
    public void testDatabasePersistence() {
        System.out.println("=== TEST: Database Metadata Storage ===");

        // 1. Create a dummy FileMetadata object
        FileMetadata file = FileMetadata.builder()
                .filename("secret_plans.pdf")
                .fileSize(1024500)
                .encryptionKey("DUMMY_BASE64_KEY_12345") // In real app, comes from EncryptionService
                .iv("DUMMY_IV_VECTOR_ABC")
                .uploadDate(LocalDateTime.now())
                .build();

        // 2. Add 6 Shards to it (Simulating the split)
        for (int i = 0; i < 6; i++) {
            ShardMetadata shard = ShardMetadata.builder()
                    .shardIndex(i)
                    .minioBucketName("omnicloud-node-" + (i + 1))
                    .status(ShardStatus.ALIVE)
                    .build();

            // This helper method links them together
            file.addShard(shard);
        }

        // 3. Save to PostgreSQL (Cascade should save shards too)
        FileMetadata savedFile = repository.save(file);
        UUID fileId = savedFile.getId();

        System.out.println("✅ Saved File ID: " + fileId);

        // 4. Retrieve from Database to verify
        FileMetadata retrievedFile = repository.findById(fileId).orElseThrow();

        // 5. Assertions
        assertEquals("secret_plans.pdf", retrievedFile.getFilename());
        assertEquals(6, retrievedFile.getShards().size());
        assertEquals("omnicloud-node-1", retrievedFile.getShards().get(0).getMinioBucketName());

        System.out.println("✅ Verified: File retrieved with " + retrievedFile.getShards().size() + " shards attached.");
        System.out.println("✅ Database Layer is operational.");
    }
}