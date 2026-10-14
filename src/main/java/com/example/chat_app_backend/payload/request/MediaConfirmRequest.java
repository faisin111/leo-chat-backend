package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MediaConfirmRequest(@NotBlank @Size(max = 180) String storageKey) {}
