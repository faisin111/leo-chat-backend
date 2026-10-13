package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

public record ReadMessageRequest(
    @PositiveOrZero
        @Schema(
            description = "Highest sequence read, between zero and conversation lastSeq",
            example = "1")
        long upToSeq) {}
