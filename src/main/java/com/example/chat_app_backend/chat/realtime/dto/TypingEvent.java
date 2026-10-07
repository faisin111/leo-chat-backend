package com.example.chat_app_backend.chat.realtime.dto;

import java.util.UUID;

public record TypingEvent(UUID conversationId, UUID userId, String username, boolean isTyping) {}
