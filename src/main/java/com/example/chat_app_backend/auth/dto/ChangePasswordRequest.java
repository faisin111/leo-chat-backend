package com.example.chat_app_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 6, max = 120) String newPassword
) {}
