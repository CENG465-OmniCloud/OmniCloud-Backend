package com.omnicloud.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    // Gerçek bir DB yerine şimdilik hafızada tutuyoruz (Demo için yeterli)
    public static Map<String, Object> currentPolicy = new HashMap<>();

    static {
        // Varsayılan ayarlar
        currentPolicy.put("blocked_regions", new String[]{"cn-north-1", "ru-west-1"});
        currentPolicy.put("replication_factor", "1.5");
        currentPolicy.put("encryption_standard", "AES-256");
    }

    @Operation(summary = "Update Geo-Fencing Policies", description = "Set allowed or blocked regions for data placement.")
    @PutMapping("/geo-fence")
    public ResponseEntity<Map<String, Object>> updatePolicy(@RequestBody Map<String, Object> newPolicy) {
        currentPolicy.putAll(newPolicy);
        currentPolicy.put("status", "UPDATED");
        return ResponseEntity.ok(currentPolicy);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPolicy() {
        return ResponseEntity.ok(currentPolicy);
    }
}