package com.omnicloud.api.controller;

import com.omnicloud.api.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final StorageService storageService;

    public AdminController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public ResponseEntity<List<String>> checkHealth() {
        return ResponseEntity.ok(storageService.getSystemHealth());
    }
}
