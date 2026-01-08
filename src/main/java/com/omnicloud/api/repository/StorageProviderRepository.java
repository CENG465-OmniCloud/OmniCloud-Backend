package com.omnicloud.api.repository;

import com.omnicloud.api.model.FileMetadata;
import com.omnicloud.api.model.StorageProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StorageProviderRepository extends JpaRepository<StorageProvider, Long> {
}
