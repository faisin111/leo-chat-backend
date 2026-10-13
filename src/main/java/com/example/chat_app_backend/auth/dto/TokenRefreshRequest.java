package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRefreshRequest(
    @NotBlank
        @Size(max = 4096)
        @Schema(description = "Refresh JWT from login; submit in JSON, not cookies")
        String refreshToken) {}
