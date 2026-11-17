package com.example.chat_app_backend.chat.ai.dto;

public record AiActionResponse(
    String action, boolean executed, boolean confirmationRequired, String message, Object data) {}
