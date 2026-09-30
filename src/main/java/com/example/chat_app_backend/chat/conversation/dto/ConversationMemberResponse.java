package com.example.chat_app_backend.chat.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationMemberResponse(
    UUID conversationId,
    UUID userId,
    String role,
    long lastReadSeq,
    Instant mutedUntil,
    Instant joinedAt,
    Instant leftAt,
    Instant pinnedAt,
    Instant archivedAt
) {}
