package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank
        @Size(max = 4096)
        @Schema(description = "Password-reset JWT bound to the current password hash")
        String token,
    @NotBlank @Size(min = 8, max = 40) String newPassword) {}
