package com.example.chat_app_backend.chat.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** WebRTC signaling envelope. Media never passes through the chat server. */
public record CallSignalRequest(
    @NotNull UUID conversationId,
    @NotNull UUID callId,
    @NotNull UUID targetUserId,
    @NotBlank String action,
    String sdp,
    String candidate,
    String sdpMid,
    Integer sdpMLineIndex) {}
