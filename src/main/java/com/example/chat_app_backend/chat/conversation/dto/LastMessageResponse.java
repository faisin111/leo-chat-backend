package com.example.chat_app_backend.chat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record LastMessageResponse(
    UUID id, UUID senderId, String type, String content, Instant createdAt, boolean deleted) {}
