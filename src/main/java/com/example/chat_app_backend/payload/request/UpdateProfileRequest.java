package com.example.chat_app_backend.payload.request;

public record UpdateProfileRequest(
    String phoneNumber,
    String bio,
    Integer age,
    String region
) {}
