package com.example.chat_app_backend.chat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    String type,
    String title,
    String avatarUrl,
    String directKey,
    UUID createdBy,
    long lastSeq,
    Instant lastMessageAt,
    Instant createdAt,
    String status) {}
