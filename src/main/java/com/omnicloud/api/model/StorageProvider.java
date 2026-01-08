package com.omnicloud.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "storage_providers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StorageProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "AWS-Virginia", "Local-Minio-1"

    @Column(nullable = false)
    private String type; // "AWS", "MINIO", "AZURE"

    @Column(nullable = false)
    private String endpointUrl; // e.g., "https://s3.amazonaws.com" or "http://localhost:9001"

    @Column(nullable = false)
    private String region; // e.g., "us-east-1" (Important for Geo-fencing later!)

    @Column(nullable = false)
    private String bucketName;

    // SECURITY WARNING: In a real app, these should be encrypted!
    @Column(nullable = false)
    @JsonIgnore // Never return the secret key in a GET request
    private String accessKey;

    @Column(nullable = false)
    @JsonIgnore // Never return the secret key in a GET request
    private String secretKey;

    @Column(nullable = false)
    @Builder.Default  // <--- ADD THIS ANNOTATION
    private boolean isEnabled = true;
}