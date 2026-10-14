package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MediaPresignRequest(
    @NotBlank @Size(max = 64) String mimeType, @Min(1) @Max(52428800) long sizeInBytes) {}
