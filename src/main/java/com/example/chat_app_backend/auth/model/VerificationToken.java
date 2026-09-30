package com.example.chat_app_backend.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "verification_tokens")
public class VerificationToken {
    
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String type; // EMAIL_VERIFICATION or PASSWORD_RESET

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public VerificationToken() {}

    public VerificationToken(String token, UUID userId, String type, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.type = type;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
