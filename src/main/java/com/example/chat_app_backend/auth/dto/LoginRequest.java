package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for user login")
public record LoginRequest(
    @Schema(description = "User's registered username", example = "john_doe")
    @NotBlank(message = "Username cannot be blank")
    String username,

    @Schema(description = "User's password", example = "SecurePass123!")
    @NotBlank(message = "Password cannot be blank")
    String password
) {}
