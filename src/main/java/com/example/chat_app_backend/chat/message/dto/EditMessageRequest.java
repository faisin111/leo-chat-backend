package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
    @NotBlank
        @Size(max = 4000)
        @Schema(description = "Message content, at most 4000 characters", example = "Hello")
        String content) {}
