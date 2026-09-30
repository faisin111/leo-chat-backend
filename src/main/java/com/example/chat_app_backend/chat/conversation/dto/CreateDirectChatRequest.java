package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDirectChatRequest(
    @NotNull UUID targetUserId
) {}
