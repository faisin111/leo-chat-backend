package com.example.chat_app_backend.payload.request;

public record UpdateProfileRequest(
    String profilePictureUrl,
    String phoneNumber,
    String bio,
    Integer age,
    String region,
    String displayName // Adding displayName for V2
    ) {}
