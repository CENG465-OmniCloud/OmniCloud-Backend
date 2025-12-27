package com.omnicloud.api.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shard_metadata")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ShardMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int shardIndex; // 0 to 5

    private String minioBucketName; // e.g., "omnicloud-node-1"

    @Enumerated(EnumType.STRING)
    private ShardStatus status;

    // Link back to the parent file
    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata fileMetadata;
}