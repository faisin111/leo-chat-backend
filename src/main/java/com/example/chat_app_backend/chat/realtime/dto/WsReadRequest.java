package com.example.chat_app_backend.chat.realtime.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record WsReadRequest(@NotNull UUID conversationId, @PositiveOrZero long upToSeq) {}
