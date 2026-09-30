package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MuteRequest(
    @NotNull Instant mutedUntil
) {}
