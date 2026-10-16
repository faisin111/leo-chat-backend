package com.example.chat_app_backend.payload.response;

import java.time.Instant;
import java.util.UUID;

/** Safe admin user view. Never expose passwordHash or JPA relationships. */
public record AdminUserResponse(
    UUID id,
    String username,
    String email,
    String displayName,
    String role,
    String status,
    boolean mustChangePassword,
    boolean emailVerified,
    Instant lastSeenAt,
    String statusReason,
    Instant statusChangedAt,
    Instant createdAt,
    Instant updatedAt) {}
