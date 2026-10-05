package com.example.chat_app_backend.payload.request;
import jakarta.validation.constraints.NotBlank;
public record MediaConfirmRequest(
    @NotBlank String storageKey
) {}
