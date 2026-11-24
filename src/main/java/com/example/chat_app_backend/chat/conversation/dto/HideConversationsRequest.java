package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record HideConversationsRequest(@NotBlank String scope, UUID conversationId, UUID userId) {}
