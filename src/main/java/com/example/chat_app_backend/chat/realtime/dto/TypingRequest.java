package com.example.chat_app_backend.chat.realtime.dto;

import java.util.UUID;

public record TypingRequest(UUID conversationId, boolean isTyping) {}
