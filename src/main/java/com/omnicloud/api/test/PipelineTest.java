package com.omnicloud.api.test;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.service.EncryptionService;
import com.omnicloud.api.service.ErasureService;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PipelineTest {

    private final ErasureService erasureService = new ErasureService();
    private final EncryptionService encryptionService = new EncryptionService();

    @Test
    public void testFullPipeline() throws Exception {
        System.out.println("=== PHASE 1: ENCRYPTION & PIPELINE TEST ===");

        // 1. ORIGINAL DATA
        String originalText = "OmniCloud: Security and Durability Test Data";
        byte[] inputBytes = originalText.getBytes(StandardCharsets.UTF_8);
        System.out.println("[1] Original: " + originalText);

        // 2. KEY GENERATION
        SecretKey key = encryptionService.generateKey();
        IvParameterSpec iv = encryptionService.generateIv();
        System.out.println("[2] Key Generated: " + encryptionService.keyToString(key));

        // 3. ENCRYPTION (AES-256)
        byte[] encryptedBytes = encryptionService.encrypt(inputBytes, key, iv);
        System.out.println("[3] Encrypted Size: " + encryptedBytes.length + " bytes");

        // 4. FRAGMENTATION (Erasure Coding)
        List<Shard> shards = erasureService.encode(encryptedBytes);
        System.out.println("[4] Split into " + shards.size() + " shards.");

        // 5. SIMULATION: DATA LOSS (Disaster)
        // Delete 2 shards (Simulates failure of MinIO containers).
        List<Shard> survivors = new ArrayList<>(shards);
        survivors.remove(0);
        survivors.remove(3);
        System.out.println("[5] Disaster! 2 shards lost. Remaining: " + survivors.size());

        // 6. RECONSTRUCTION
        // Reconstruct the encrypted data from the remaining 4 shards.
        byte[] recoveredEncrypted = erasureService.decode(survivors, encryptedBytes.length);

        // 7. DECRYPTION
        String keyAsString = encryptionService.keyToString(key);
        SecretKey restoredKey = encryptionService.stringToKey(keyAsString);

        byte[] decryptedBytes = encryptionService.decrypt(recoveredEncrypted, restoredKey, iv);

        String finalText = new String(decryptedBytes, StandardCharsets.UTF_8);
        System.out.println("[6] Decrypted: " + finalText);

        // 8. VERIFICATION
        assertEquals(originalText, finalText);
        System.out.println("SUCCESS: Pipeline is fully functional!");
    }
}