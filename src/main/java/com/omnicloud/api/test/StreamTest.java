package com.omnicloud.api.test;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.service.EncryptionService;
import com.omnicloud.api.service.ErasureService;
import com.omnicloud.api.service.StreamService;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamTest {

    @Test
    public void testChunkProcessing() throws Exception {
        System.out.println("=== TEST: Stream Processing Logic ===");

        // 1. Setup Services
        ErasureService erasureService = new ErasureService();
        EncryptionService encryptionService = new EncryptionService(); // Ensure this has empty constructor or use @Autowired in SpringBootTest
        StreamService streamService = new StreamService(erasureService, encryptionService);

        // 2. Generate a "Big" Dummy File in Memory (12MB)
        // This is enough to trigger 3 chunks (5MB + 5MB + 2MB)
        int fileSize = 12 * 1024 * 1024;
        byte[] dummyFile = new byte[fileSize];
        new Random().nextBytes(dummyFile); // Fill with random noise
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dummyFile);

        // 3. Generate Keys
        SecretKey key = encryptionService.generateKey();
        IvParameterSpec iv = encryptionService.generateIv();

        // 4. Run the Processor
        AtomicInteger totalChunks = new AtomicInteger(0);

        streamService.processStream(inputStream, key, iv, (shards) -> {
            // This code runs every time a 5MB chunk is ready
            int chunkNum = totalChunks.incrementAndGet();
            System.out.println(">> Callback received " + shards.size() + " shards for Chunk " + chunkNum);

            // Verify we got 6 shards (4 data + 2 parity)
            if (shards.size() != 6) {
                throw new RuntimeException("Wrong shard count!");
            }
        });

        // 5. Verification
        System.out.println("✅ Processing Complete.");

        // We expect 3 chunks:
        // Chunk 1: 5MB
        // Chunk 2: 5MB
        // Chunk 3: ~2MB + padding
        assertTrue(totalChunks.get() >= 3, "Should have processed at least 3 chunks");
    }
}