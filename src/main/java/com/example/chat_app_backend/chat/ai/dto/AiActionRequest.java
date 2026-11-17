package com.example.chat_app_backend.chat.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AiActionRequest(
    @NotBlank @Size(max = 40) String action,
    UUID conversationId,
    UUID targetUserId,
    @Size(max = 4000) String message,
    @Size(max = 100) String query,
    boolean confirmed) {}
