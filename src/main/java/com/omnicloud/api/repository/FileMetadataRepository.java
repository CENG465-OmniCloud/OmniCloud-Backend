package com.omnicloud.api.repository;

import com.omnicloud.api.model.FileMetadata;
import com.omnicloud.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    List<FileMetadata> findAllByOwner(User currentUser);
}