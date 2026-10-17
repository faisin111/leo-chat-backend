package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
    @Schema(description = "Server-assigned message ID") UUID id,
    @Schema(description = "Conversation ID") UUID conversationId,
    @Schema(description = "Authenticated sender ID") UUID senderId,
    @Schema(description = "Sender username") String senderUsername,
    @Schema(description = "Sender display name") String senderDisplayName,
    @Schema(description = "Server-assigned conversation sequence", example = "1") long seq,
    @Schema(description = "Client-generated retry key, at most 64 characters", example = "msg-1")
        String clientMessageId,
    @Schema(
            description = "Message type: TEXT, IMAGE, or FILE; SYSTEM is server-only",
            example = "TEXT")
        String type,
    @Schema(description = "Message content, at most 4000 characters", example = "Hello")
        String content,
    @Schema(description = "Optional message ID in this conversation") UUID replyToId,
    @Schema(description = "Reply target preview, if this message is a reply")
        MessageReplyResponse replyTo,
    @Schema(description = "Reactions grouped by emoji and visible to all members")
        List<MessageReactionResponse> reactions,
    @Schema(description = "Creation time in UTC") Instant createdAt,
    @Schema(description = "Edit time in UTC, if edited") Instant editedAt,
    @Schema(description = "Deletion time in UTC, if deleted") Instant deletedAt,
    @Schema(description = "True when the message was deleted; content is then null")
        boolean deleted) {}
