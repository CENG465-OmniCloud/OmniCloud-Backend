package com.omnicloud.api.model;

import jakarta.persistence.*; // JPA importları
import java.time.LocalDateTime;

@Entity // Bu sınıfın bir tablo olduğunu söyler
@Table(name = "audit_logs") // Tablo adı
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Veritabanı için Benzersiz ID (PK)

    private LocalDateTime timestamp;
    private String action;
    private String username; // 'user' kelimesi SQL'de yasaklı olabilir, 'username' yaptım
    private String details;
    private String status;

    // Boş Constructor (JPA için zorunlu)
    public AuditLog() {}

    // Bizim kullanacağımız Constructor
    public AuditLog(String action, String username, String details, String status) {
        this.timestamp = LocalDateTime.now();
        this.action = action;
        this.username = username;
        this.details = details;
        this.status = status;
    }

    // Getter & Setter (Lombok kullanıyorsan @Data ekleyip bunları silebilirsin)
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getAction() { return action; }
    public String getUsername() { return username; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
}