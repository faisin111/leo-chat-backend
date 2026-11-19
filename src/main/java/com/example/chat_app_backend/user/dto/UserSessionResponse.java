package com.example.chat_app_backend.user.dto;

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
    String deviceName,
    String platform,
    String appVersion,
    boolean active) {}
