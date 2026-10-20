package com.example.chat_app_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Bearer token response backed by a server refresh session")
public record TokenResponse(
    @Schema(description = "Short-lived access JWT") String accessToken,
    @Schema(description = "Authorization scheme", example = "Bearer") String tokenType,
    @Schema(description = "Refresh JWT; never send it to protected API routes") String refreshToken,
    @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
    @Schema(description = "Remaining refresh token lifetime in seconds", example = "604800")
        long refreshExpiresIn,
    @Schema(description = "Authenticated user ID") UUID id,
    @Schema(description = "Authenticated username") String username,
    @Schema(description = "Authenticated email address") String email) {}
