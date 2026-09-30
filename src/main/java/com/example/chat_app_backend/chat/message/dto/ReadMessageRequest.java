package com.example.chat_app_backend.chat.message.dto;

public record ReadMessageRequest(
    long upToSeq
) {}
