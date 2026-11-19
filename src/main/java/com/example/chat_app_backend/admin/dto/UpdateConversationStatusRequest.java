package com.example.chat_app_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateConversationStatusRequest(
    @NotBlank @Pattern(regexp = "ACTIVE|ARCHIVED|DELETED") String status,
    @Size(max = 500) String reason) {}
