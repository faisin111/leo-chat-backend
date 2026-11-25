package com.example.chat_app_backend.notification;

import java.time.Instant;
import java.util.UUID;

public record AppNotificationResponse(
    UUID id,
    String type,
    String title,
    String body,
    UUID referenceId,
    UUID conversationId,
    Instant createdAt,
    Instant readAt,
    boolean read) {
  static AppNotificationResponse from(AppNotification notification) {
    return new AppNotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getBody(),
        notification.getReferenceId(),
        notification.getConversationId(),
        notification.getCreatedAt(),
        notification.getReadAt(),
        notification.getReadAt() != null);
  }
}
