package com.example.chat_app_backend.chat.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AiChatRequest(
    @NotNull UUID conversationId,
    @NotBlank @Size(max = 4000) String prompt,
    @Size(max = 64) String clientMessageId) {}
