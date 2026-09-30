package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.Size;

public record UpdateGroupChatRequest(
    @Size(max = 100) String title,
    @Size(max = 500) String avatarUrl
) {}
