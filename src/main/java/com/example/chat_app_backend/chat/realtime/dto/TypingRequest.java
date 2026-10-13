package com.example.chat_app_backend.chat.realtime.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TypingRequest(@NotNull UUID conversationId, boolean isTyping) {}
