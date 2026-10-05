package com.example.chat_app_backend.chat.message.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    long seq,
    String clientMessageId,
    String type,
    String content,
    UUID replyToId,
    Instant createdAt,
    Instant editedAt,
    Instant deletedAt) {}
