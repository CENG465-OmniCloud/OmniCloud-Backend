package com.omnicloud.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "file_metadata")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long fileSize; // Original size in bytes

    @Column(nullable = false, length = 1000)
    private String encryptionKey; // Base64 Encoded AES Key

    @Column(nullable = false)
    private String iv; // Base64 Encoded Initialization Vector

    private LocalDateTime uploadDate;

    // One File has Many Shards
    // CascadeType.ALL means if we save the File, it auto-saves the Shards.
    @OneToMany(mappedBy = "fileMetadata", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ShardMetadata> shards = new ArrayList<>();

    // Helper method to keep both sides of the relationship in sync
    public void addShard(ShardMetadata shard) {
        shards.add(shard);
        shard.setFileMetadata(this);
    }
}