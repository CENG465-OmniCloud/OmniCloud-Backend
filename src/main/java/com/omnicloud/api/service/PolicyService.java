package com.omnicloud.api.service;

import com.omnicloud.api.model.User;
import com.omnicloud.api.model.UserPolicy;
import com.omnicloud.api.repository.UserPolicyRepository;
import com.omnicloud.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final UserPolicyRepository policyRepository;
    private final UserRepository userRepository;

    // Helper to get current logged-in user
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserPolicy getMyPolicy() {
        User user = getCurrentUser();
        return policyRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    // Fallback: If no policy exists (old users), create one
                    return policyRepository.save(UserPolicy.builder().user(user).build());
                });
    }

    public UserPolicy updateBlockedRegions(List<String> regions) {
        UserPolicy policy = getMyPolicy();
        policy.setBlockedRegions(regions);
        return policyRepository.save(policy);
    }
}