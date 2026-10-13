package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
    @NotNull @Schema(description = "Conversation ID") UUID conversationId,
    @NotBlank
        @Size(max = 64)
        @Schema(
            description = "Client-generated retry key, at most 64 characters",
            example = "msg-1")
        String clientMessageId,
    @Schema(
            description = "Message type: TEXT, IMAGE, or FILE; SYSTEM is server-only",
            example = "TEXT")
        String type,
    @NotBlank
        @Size(max = 4000)
        @Schema(description = "Message content, at most 4000 characters", example = "Hello")
        String content,
    @Schema(description = "Optional message ID in this conversation") UUID replyToId) {}
