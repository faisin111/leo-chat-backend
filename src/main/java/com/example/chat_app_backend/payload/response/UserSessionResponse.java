package com.example.chat_app_backend.payload.response;

import java.time.Instant;
import java.util.UUID;

public record UserSessionResponse(
    UUID sessionId,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    Instant logoutAt,
    String userAgent,
    String ipAddress,
    boolean active) {}
