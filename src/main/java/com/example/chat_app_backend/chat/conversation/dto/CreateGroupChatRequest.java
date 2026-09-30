package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateGroupChatRequest(
    @NotBlank @Size(max = 100) String title,
    @NotEmpty List<UUID> memberIds
) {}
