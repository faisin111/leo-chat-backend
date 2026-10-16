package com.example.chat_app_backend.chat.message.dto;

import java.util.UUID;

/** Small, non-recursive preview of the message being replied to. */
public record MessageReplyResponse(
    UUID id,
    UUID senderId,
    String senderUsername,
    String senderDisplayName,
    String content,
    boolean deleted) {}
