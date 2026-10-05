package com.example.chat_app_backend.chat.message.dto;

import jakarta.validation.constraints.NotBlank;

public record EditMessageRequest(@NotBlank String content) {}
