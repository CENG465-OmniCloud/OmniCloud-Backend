package com.omnicloud.api.controller;

import com.omnicloud.api.dto.ProviderRequest;
import com.omnicloud.api.model.StorageProvider;
import com.omnicloud.api.service.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping
    public ResponseEntity<StorageProvider> addProvider(@RequestBody ProviderRequest request) {
        return ResponseEntity.ok(providerService.addProvider(request));
    }

    @GetMapping
    public ResponseEntity<List<StorageProvider>> listProviders() {
        return ResponseEntity.ok(providerService.getAllProviders());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<StorageProvider>> addMultipleProviders(@RequestBody List<ProviderRequest> requests) {
        List<StorageProvider> savedProviders = new ArrayList<>();
        for (ProviderRequest req : requests) {
            savedProviders.add(providerService.addProvider(req));
        }
        return ResponseEntity.ok(savedProviders);
    }
}