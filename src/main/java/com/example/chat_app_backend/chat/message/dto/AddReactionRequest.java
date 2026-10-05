package com.example.chat_app_backend.chat.message.dto;

import jakarta.validation.constraints.NotBlank;

public record AddReactionRequest(@NotBlank String emoji) {}
