package com.omnicloud.api.service;

import com.omnicloud.api.model.Shard;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

@Service
public class StreamService {

    private final ErasureService erasureService;
    private final EncryptionService encryptionService;

    // 5MB Chunk Size (Standard for S3 Multipart Uploads)
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;

    public StreamService(ErasureService erasureService, EncryptionService encryptionService) {
        this.erasureService = erasureService;
        this.encryptionService = encryptionService;
    }

    /**
     * PROCESS: Stream -> Encrypt -> Chunk -> Split -> Upload
     * * @param inputStream The raw file stream from the user
     * @param key The AES key generated for this file
     * @param iv The IV for encryption
     * @param shardProcessor A "Callback" function that handles the uploading of shards (we define this later)
     */
    public void processStream(InputStream inputStream, SecretKey key, IvParameterSpec iv, Consumer<List<Shard>> shardProcessor) throws Exception {

        // 1. Setup Encryption Stream
        // Any byte read from 'cipherStream' is automatically AES encrypted
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        try (CipherInputStream cipherStream = new CipherInputStream(inputStream, cipher)) {

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            // 2. Read loop (The "Bucket Brigade")
            // We read until the file is finished (-1)
            while (true) {
                // Read until buffer is full or stream ends
                bytesRead = readBufferFully(cipherStream, buffer);

                if (bytesRead <= 0) break; // End of file

                // Prepare the data to be split
                byte[] dataToSplit;
                if (bytesRead < CHUNK_SIZE) {
                    // Resize array for the last smaller chunk
                    dataToSplit = new byte[bytesRead];
                    System.arraycopy(buffer, 0, dataToSplit, 0, bytesRead);
                } else {
                    dataToSplit = buffer;
                }

                // 3. Split this encrypted chunk into 6 shards (Math Magic)
                List<Shard> shards = erasureService.encode(dataToSplit);

                // 4. Pass the shards to the "Uploader" (The Callback)
                // This allows us to upload *while* we process the next chunk!
                shardProcessor.accept(shards);

                chunkIndex++;
                System.out.println("Processed Chunk #" + chunkIndex + " (" + bytesRead + " bytes)");
            }
        }
    }

    // Helper: Keeps reading until the buffer is full or stream is empty
    private int readBufferFully(InputStream in, byte[] buffer) throws Exception {
        int totalRead = 0;
        while (totalRead < buffer.length) {
            int result = in.read(buffer, totalRead, buffer.length - totalRead);
            if (result == -1) {
                break; // End of stream
            }
            totalRead += result;
        }
        return totalRead;
    }
}