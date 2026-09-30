package com.example.chat_app_backend.auth.dto;

import java.util.UUID;

public record TokenResponse(
    String accessToken,
    String tokenType,
    String refreshToken,
    UUID id,
    String username,
    String email
) {
    public TokenResponse(String accessToken, String refreshToken, UUID id, String username, String email) {
        this(accessToken, "Bearer", refreshToken, id, username, email);
    }
}
