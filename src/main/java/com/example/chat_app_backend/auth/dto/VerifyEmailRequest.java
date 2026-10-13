package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
    @NotBlank @Size(max = 4096) @Schema(description = "Email verification JWT") String token) {}
