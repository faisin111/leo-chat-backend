package com.example.chat_app_backend.payload.response;

import java.util.UUID;
import java.time.Instant;

public record UserSearchResponse(
    UUID id,
    String username,
    String displayName,
    Instant lastSeenAt,
    String status
) {}
