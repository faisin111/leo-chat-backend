package com.example.chat_app_backend.chat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
    @NotBlank @Pattern(regexp = "^(ADMIN|MEMBER)$") String role
) {}
