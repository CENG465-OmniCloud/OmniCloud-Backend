package com.omnicloud.api.controller;

import com.omnicloud.api.model.UserPolicy;
import com.omnicloud.api.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    public ResponseEntity<UserPolicy> getMyPolicy() {
        return ResponseEntity.ok(policyService.getMyPolicy());
    }

    @PutMapping("/geo-fence")
    public ResponseEntity<UserPolicy> updateGeoFence(@RequestBody Map<String, List<String>> request) {
        List<String> blockedRegions = request.get("blockedRegions");
        return ResponseEntity.ok(policyService.updateBlockedRegions(blockedRegions));
    }
}