package com.omnicloud.api.service;

import com.omnicloud.api.model.AuditLog;
import com.omnicloud.api.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    // Repository'i buraya enjekte ediyoruz
    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    // Logu veritabanına kaydet (INSERT INTO...)
    public void log(String action, String user, String details, String status) {
        AuditLog newLog = new AuditLog(action, user, details, status);
        repository.save(newLog); // Veritabanına yazar
    }

    // Logları veritabanından oku (SELECT * FROM...)
    public List<AuditLog> getLogs() {
        return repository.findAllByOrderByTimestampDesc();
    }
}