package com.omnicloud.api.config;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MinioConfig {

    public static final String COMMON_BUCKET_NAME = "omnicloud-data";

    // --- CLOUD 1: AWS US-EAST ---
    @Value("${minio.aws.endpoint:http://localhost:9001}")
    private String awsEndpoint;
    @Value("${minio.aws.accessKey}")
    private String awsAccessKey;
    @Value("${minio.aws.secretKey}")
    private String awsSecretKey;

    @Bean(name = "awsClient")
    public MinioClient awsClient() {
        return buildClient(awsEndpoint, awsAccessKey, awsSecretKey);
    }

    // --- CLOUD 2: AWS EU-CENTRAL ---
    @Value("${minio.aws-eu.endpoint:http://localhost:9002}")
    private String awsEuEndpoint;

    @Bean(name = "awsEuClient")
    public MinioClient awsEuClient() {
        return buildClient(awsEuEndpoint, awsAccessKey, awsSecretKey);
    }

    // --- CLOUD 3: AZURE ---
    @Value("${minio.azure.endpoint:http://localhost:9003}")
    private String azureEndpoint;
    @Value("${minio.azure.accessKey}")
    private String azureAccessKey;
    @Value("${minio.azure.secretKey}")
    private String azureSecretKey;

    @Bean(name = "azureClient")
    public MinioClient azureClient() {
        return buildClient(azureEndpoint, azureAccessKey, azureSecretKey);
    }

    // --- CLOUD 4: GCP ---
    @Value("${minio.gcp.endpoint:http://localhost:9004}")
    private String gcpEndpoint;
    @Value("${minio.gcp.accessKey}")
    private String gcpAccessKey;
    @Value("${minio.gcp.secretKey}")
    private String gcpSecretKey;

    @Bean(name = "gcpClient")
    public MinioClient gcpClient() {
        return buildClient(gcpEndpoint, gcpAccessKey, gcpSecretKey);
    }

    // --- CLOUD 5: ORACLE ---
    @Value("${minio.oracle.endpoint:http://localhost:9005}")
    private String oracleEndpoint;
    @Value("${minio.oracle.accessKey}")
    private String oracleAccessKey;
    @Value("${minio.oracle.secretKey}")
    private String oracleSecretKey;

    @Bean(name = "oracleClient")
    public MinioClient oracleClient() {
        return buildClient(oracleEndpoint, oracleAccessKey, oracleSecretKey);
    }

    // --- CLOUD 6: LOCAL ---
    @Value("${minio.local.endpoint:http://localhost:9006}")
    private String localEndpoint;
    @Value("${minio.local.accessKey}")
    private String localAccessKey;
    @Value("${minio.local.secretKey}")
    private String localSecretKey;

    @Bean(name = "localClient")
    public MinioClient localClient() {
        return buildClient(localEndpoint, localAccessKey, localSecretKey);
    }

    @Bean
    public Map<Integer, MinioClient> minioClients() {
        Map<Integer, MinioClient> clients = new HashMap<>();
        clients.put(0, awsClient());
        clients.put(1, awsEuClient());
        clients.put(2, azureClient());
        clients.put(3, gcpClient());
        clients.put(4, oracleClient());
        clients.put(5, localClient());
        return clients;
    }

    // Helper method
    private MinioClient buildClient(String endpoint, String accessKey, String secretKey) {
        try {
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO client for endpoint: " + endpoint, e);
        }
    }
}