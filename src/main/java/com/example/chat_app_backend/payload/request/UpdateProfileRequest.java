package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 2000) String profilePictureUrl,
    @Size(max = 32) String phoneNumber,
    @Size(max = 2000) String bio,
    @Min(0) @Max(150) Integer age,
    @Size(max = 100) String region,
    @Size(max = 80) String displayName // Adding displayName for V2
    ) {}
