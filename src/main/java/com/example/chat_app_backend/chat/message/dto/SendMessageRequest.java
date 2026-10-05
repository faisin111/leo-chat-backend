package com.example.chat_app_backend.chat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SendMessageRequest(
    @NotNull UUID conversationId,
    @NotBlank String clientMessageId,
    String type,
    String content,
    UUID replyToId) {}
