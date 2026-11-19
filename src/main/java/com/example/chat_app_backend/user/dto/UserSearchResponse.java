package com.example.chat_app_backend.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSearchResponse(
    UUID id,
    String username,
    String displayName,
    Instant lastSeenAt,
    String status,
    String profilePictureUrl,
    String bio,
    String region,
    Integer age) {}
