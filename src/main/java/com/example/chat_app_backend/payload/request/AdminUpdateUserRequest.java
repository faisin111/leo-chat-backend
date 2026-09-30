package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.Email;

public record AdminUpdateUserRequest(
    String username,
    @Email String email,
    String profilePictureUrl
) {}
