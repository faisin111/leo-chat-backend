package com.example.chat_app_backend.chat.realtime.dto;

import java.util.UUID;

/** Signal delivered to the target user's call queue. */
public record CallSignalEvent(
    UUID conversationId,
    UUID callId,
    UUID fromUserId,
    String fromUsername,
    UUID targetUserId,
    String action,
    String sdp,
    String candidate,
    String sdpMid,
    Integer sdpMLineIndex) {}
