package com.omnicloud.api.service;

import com.omnicloud.api.dto.ProviderRequest;
import com.omnicloud.api.model.StorageProvider;
import com.omnicloud.api.repository.StorageProviderRepository;
import org.springframework.stereotype.Service;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;

import java.util.List;

@Service
public class ProviderService {

    private final StorageProviderRepository repository;

    public ProviderService(StorageProviderRepository repository) {
        this.repository = repository;
    }

    public StorageProvider addProvider(ProviderRequest request) {
        // 1. Validation: Try to connect before saving (Optional but recommended)
        validateConnection(request);

        // 2. Map DTO to Entity
        StorageProvider provider = StorageProvider.builder()
                .name(request.getName())
                .type(request.getType())
                .endpointUrl(request.getEndpointUrl())
                .region(request.getRegion())
                .bucketName(request.getBucketName())
                .accessKey(request.getAccessKey()) // In real life: encrypt this!
                .secretKey(request.getSecretKey()) // In real life: encrypt this!
                .isEnabled(true)
                .build();

        return repository.save(provider);
    }

    private void validateConnection(ProviderRequest request) {
        System.out.println("🔌 Testing connection to: " + request.getName());

        try {
            // 1. Create a temporary client just for this test
            MinioClient testClient = MinioClient.builder()
                    .endpoint(request.getEndpointUrl())
                    .credentials(request.getAccessKey(), request.getSecretKey())
                    .build();

            // 2. Try a lightweight operation (check if bucket exists)
            // If credentials are wrong or URL is unreachable, this will throw an exception.
            boolean found = testClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(request.getBucketName())
                    .build());

            // 3. Optional: If bucket doesn't exist, we could auto-create it here!
            if (!found) {
                System.out.println("⚠️ Bucket not found. You might need to create it: " + request.getBucketName());
                // You could throw an error here if you want to be strict:
                // throw new RuntimeException("Bucket does not exist on this provider!");
            }

            System.out.println("✅ Connection Successful!");

        } catch (Exception e) {
            // 4. If ANY error happens (Auth, Network, etc.), block the save.
            System.err.println("❌ Connection Failed: " + e.getMessage());
            throw new RuntimeException("Could not connect to provider: " + e.getMessage());
        }
    }

    public List<StorageProvider> getAllProviders() {
        return repository.findAll();
    }

    public void deleteProvider(Long id) {
        repository.deleteById(id);
    }
}