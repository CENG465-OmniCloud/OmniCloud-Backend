package com.omnicloud.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_policies")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link this policy to a specific User
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore // Prevent infinite recursion in JSON
    private User user;

    // The regions this specific user wants to block
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_blocked_regions", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "region")
    @Builder.Default
    private List<String> blockedRegions = new ArrayList<>();
}