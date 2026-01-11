package com.omnicloud.api.repository;

import com.omnicloud.api.model.User;
import com.omnicloud.api.model.UserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPolicyRepository extends JpaRepository<UserPolicy, Long> {
    Optional<UserPolicy> findByUserId(long userId);
}