package com.example.chat_app_backend.chat.message.dto;

import java.util.List;

/** Aggregated reaction visible to every conversation member. */
public record MessageReactionResponse(
    String emoji, long count, List<MessageReactionUserResponse> users) {}
