package com.example.chat_app_backend.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MediaConfirmRequest(@NotBlank @Size(max = 180) String storageKey) {}
