package com.example.chat_app_backend.chat.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Replace an AI answer with a new one for the same user prompt. */
public record AiRegenerateRequest(
    @NotNull UUID conversationId,
    @NotNull UUID messageId,
    @Pattern(regexp = AiOptions.MODEL_PATTERN) String model,
    Boolean reasoning,
    Boolean webSearch) {}
