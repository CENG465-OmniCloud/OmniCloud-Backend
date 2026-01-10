package com.omnicloud.api.controller;

import com.omnicloud.api.model.FileMetadata;
import com.omnicloud.api.repository.FileMetadataRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final FileMetadataRepository repository;

    public AnalyticsController(FileMetadataRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Storage Usage & Cost Savings", description = "Calculates storage efficiency compared to traditional replication.")
    @GetMapping("/storage")
    public ResponseEntity<Map<String, Object>> getStorageAnalytics() {
        List<FileMetadata> allFiles = repository.findAll();

        long totalOriginalSize = allFiles.stream().mapToLong(FileMetadata::getFileSize).sum();
        long erasureCodedSize = (long) (totalOriginalSize * 1.5); // 4 Data + 2 Parity = 1.5x overhead
        long traditionalReplicationSize = totalOriginalSize * 3;   // Standart cloud replication = 3x overhead

        Map<String, Object> response = new HashMap<>();
        response.put("total_files_count", allFiles.size());
        response.put("total_data_stored_bytes", totalOriginalSize);
        response.put("omnicloud_storage_usage_bytes", erasureCodedSize);
        response.put("traditional_cloud_usage_bytes", traditionalReplicationSize);
        response.put("storage_saved_bytes", traditionalReplicationSize - erasureCodedSize);
        response.put("cost_saving_percentage", "50%"); // (3x - 1.5x) / 3x = %50 tasarruf

        return ResponseEntity.ok(response);
    }
}