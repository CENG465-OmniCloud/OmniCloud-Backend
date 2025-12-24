package com.omnicloud.api.service;

import com.backblaze.erasure.ReedSolomon;
import com.omnicloud.api.model.Shard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ErasureService {
    // Configuration: 6 Total, 4 Data. Can survive 2 failures.
    private static final int DATA_SHARDS = 4;
    private static final int PARITY_SHARDS = 2;
    private static final int TOTAL_SHARDS = DATA_SHARDS + PARITY_SHARDS;

    // The Backblaze codec
    private final ReedSolomon codec;

    public ErasureService() {
        // Initialize the Reed-Solomon matrix tables
        this.codec = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS);
    }

    /**
     * Step 1: Split data into shards
     */
    public List<Shard> encode(byte[] data) {
        int storedSize = data.length;
        int shardSize = (storedSize + DATA_SHARDS - 1) / DATA_SHARDS; // Ceiling division

        // Create buffer for the shards (data + parity)
        byte[][] shards = new byte[TOTAL_SHARDS][shardSize];

        // Fill the first 4 shards with input data
        for (int i = 0; i < DATA_SHARDS; i++) {
            int start = i * shardSize;
            int length = Math.min(shardSize, storedSize - start);

            // Copy data into the shard buffer
            if (length > 0) {
                System.arraycopy(data, start, shards[i], 0, length);
            }
        }

        // Use the library to calculate the 2 parity shards (Magic happens here)
        codec.encodeParity(shards, 0, shardSize);

        // Wrap in our Shard object
        List<Shard> output = new ArrayList<>();
        for (int i = 0; i < TOTAL_SHARDS; i++) {
            output.add(new Shard(i, shards[i]));
        }
        return output;
    }

    /**
     * Step 2: Reconstruct data from subset of shards
     */
    public byte[] decode(List<Shard> availableShards, int originalLength) {
        if (availableShards.size() < DATA_SHARDS) {
            throw new IllegalArgumentException("Not enough shards to reconstruct! Need " + DATA_SHARDS);
        }

        // We need the size of one shard to initialize buffers
        int shardSize = availableShards.get(0).getData().length;
        byte[][] shards = new byte[TOTAL_SHARDS][shardSize];
        boolean[] shardPresent = new boolean[TOTAL_SHARDS];

        // Fill the known shards into the array
        for (Shard s : availableShards) {
            shards[s.getIndex()] = s.getData();
            shardPresent[s.getIndex()] = true;
        }

        // Use library to reconstruct missing shards
        codec.decodeMissing(shards, shardPresent, 0, shardSize);

        // Combine the Data Shards (0-3) back into one byte array
        byte[] decoded = new byte[originalLength];
        for (int i = 0; i < DATA_SHARDS; i++) {
            int start = i * shardSize;
            int length = Math.min(shardSize, originalLength - start);
            System.arraycopy(shards[i], 0, decoded, start, length);
        }

        return decoded;
    }
}
