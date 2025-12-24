package com.omnicloud.api.test;

import com.omnicloud.api.model.Shard;
import com.omnicloud.api.service.ErasureService;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class ErasureTest {
    private final ErasureService service = new ErasureService();

    @Test
    public void testErasureCodingLogic() {
        System.out.println("==========================================");
        System.out.println("    TEST 1: Simple Sentence Recovery      ");
        System.out.println("==========================================");

        String originalText = "OmniCloud is a distributed storage system that survives failure!";
        byte[] inputData = originalText.getBytes(StandardCharsets.UTF_8);

        List<Shard> allShards = service.encode(inputData);

        // Disaster
        List<Shard> survivors = new ArrayList<>();
        survivors.add(allShards.get(0));
        survivors.add(allShards.get(2));
        survivors.add(allShards.get(4));
        survivors.add(allShards.get(5));

        byte[] reconstructedData = service.decode(survivors, inputData.length);
        String resultText = new String(reconstructedData, StandardCharsets.UTF_8);

        assertEquals(originalText, resultText);
        System.out.println("SUCCESS: Sentence restored: \"" + resultText + "\"");
        System.out.println("------------------------------------------\n");
    }

    @Test
    public void testStoryRecovery() {
        System.out.println("==========================================");
        System.out.println("   TEST 2: The Library of Babel (Story)   ");
        System.out.println("==========================================");

        // 1. A Story
        String story = """
            The universe (which others call the Library) is composed of an indefinite and perhaps infinite number of hexagonal galleries.
            There are five shelves for each of the hexagon's walls; each shelf contains thirty-five books of uniform format.
            OmniCloud ensures that even if this book is torn into pieces and scattered across the clouds, it shall return.
            """;

        byte[] inputData = story.getBytes(StandardCharsets.UTF_8);
        System.out.println("Original Text Size: " + inputData.length + " bytes");

        // 2. Encode
        List<Shard> allShards = service.encode(inputData);
        System.out.println("Split into " + allShards.size() + " shards (4 Data + 2 Parity).");

        // 3. Disaster: Lose Shard 1 and Shard 4
        List<Shard> survivors = new ArrayList<>();
        survivors.add(allShards.get(0));
        survivors.add(allShards.get(2)); // Lost #1
        survivors.add(allShards.get(3));
        survivors.add(allShards.get(5)); // Lost #4

        System.out.println("Disaster! 2 shards were destroyed (Lost indices #1 and #4).");
        System.out.println("Attempting mathematical recovery...");

        // 4. Decode
        byte[] reconstructedData = service.decode(survivors, inputData.length);
        String resultText = new String(reconstructedData, StandardCharsets.UTF_8);

        // 5. Verify
        assertEquals(story, resultText);
        System.out.println("SUCCESS: The story was fully restored!");
        System.out.println("------------------------------------------\n");
    }

    @Test
    public void testRealFileRecovery() throws IOException {
        System.out.println("==========================================");
        System.out.println("   TEST 3: Real PDF File Recovery      ");
        System.out.println("==========================================");

        // Path to your PDF
        String myFilePath = "src/main/java/com/omnicloud/api/test/testfile/AhmetKaya_SevvalCakmak_CENG465_DistributedComputingProjectProposal.pdf";
        File realFile = new File(myFilePath);

        if (!realFile.exists()) {
            System.err.println("ERROR: File not found at " + myFilePath);
            return;
        }

        System.out.println("Testing with file: " + realFile.getName());
        byte[] fileBytes = Files.readAllBytes(realFile.toPath());
        System.out.println("Original File Size: " + fileBytes.length + " bytes");

        // Encode
        long start = System.currentTimeMillis();
        List<Shard> allShards = service.encode(fileBytes);
        long end = System.currentTimeMillis();
        System.out.println("Encoded in " + (end - start) + "ms.");

        // Disaster
        List<Shard> survivors = new ArrayList<>(allShards);
        survivors.remove(1);
        survivors.remove(3);
        System.out.println("Simulated Cloud Failure: Removed 2 random shards.");

        // Decode
        byte[] recoveredBytes = service.decode(survivors, fileBytes.length);

        // Verify
        assertArrayEquals(fileBytes, recoveredBytes);
        System.out.println("SUCCESS: The PDF binary is identical to the original.");
        System.out.println("------------------------------------------\n");
    }
}