package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for registering a new user")
public record RegisterRequest(
    @Schema(description = "The chosen username (3-20 characters, alphanumeric and ._- only)", example = "john_doe")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,}$", message = "Username can only contain letters, numbers, dots, dashes, and underscores")
    String username,

    @Schema(description = "User's email address", example = "john@example.com")
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be a valid email address format")
    String email,

    @Schema(description = "Highly secure password (min 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 special char)", example = "SecurePass123!")
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 40, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$", 
             message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character (@#$%^&+=!)")
    String password,

    @Schema(description = "Optional user role (e.g., 'admin', 'user'). Defaults to 'user'. Only one admin allowed.", example = "user")
    String role
) {}
