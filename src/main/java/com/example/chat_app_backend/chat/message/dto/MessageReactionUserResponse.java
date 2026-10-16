package com.example.chat_app_backend.chat.message.dto;

import java.util.UUID;

/** User identity attached to a message reaction. */
public record MessageReactionUserResponse(UUID id, String username, String displayName) {}
