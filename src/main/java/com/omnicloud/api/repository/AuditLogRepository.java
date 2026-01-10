package com.omnicloud.api.repository;

import com.omnicloud.api.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Logları tarihe göre tersten (en yeni en üstte) sıralayıp getir
    List<AuditLog> findAllByOrderByTimestampDesc();
}