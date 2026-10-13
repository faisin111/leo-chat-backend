package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddReactionRequest(
    @NotBlank
        @Size(max = 16)
        @Schema(description = "Reaction emoji, at most 16 characters", example = "👍")
        String emoji) {}
