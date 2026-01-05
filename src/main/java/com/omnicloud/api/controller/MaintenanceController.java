package com.omnicloud.api.controller;

import com.omnicloud.api.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final FileService fileService;

    public MaintenanceController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/repair/{id}")
    public ResponseEntity<String> repairFile(@PathVariable UUID id) {
        try {
            String result = fileService.repairFile(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Repair Failed: " + e.getMessage());
        }
    }
}