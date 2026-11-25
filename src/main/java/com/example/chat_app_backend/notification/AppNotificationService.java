package com.example.chat_app_backend.notification;

import com.example.chat_app_backend.common.exception.NotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppNotificationService {
  private final AppNotificationRepository repository;

  public AppNotificationService(AppNotificationRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Page<AppNotificationResponse> list(UUID userId, int page, int size, boolean unreadOnly) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 100);
    PageRequest request = PageRequest.of(safePage, safeSize);
    Page<AppNotification> notifications =
        unreadOnly
            ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, request)
            : repository.findByUserIdOrderByCreatedAtDesc(userId, request);
    return notifications.map(AppNotificationResponse::from);
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return repository.countByUserIdAndReadAtIsNull(userId);
  }

  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    AppNotification notification =
        repository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
    notification.markRead();
    repository.save(notification);
  }

  @Transactional
  public int markAllRead(UUID userId) {
    var notifications = repository.findByUserIdAndReadAtIsNull(userId);
    notifications.forEach(AppNotification::markRead);
    repository.saveAll(notifications);
    return notifications.size();
  }

  @Transactional
  public void delete(UUID userId, UUID notificationId) {
    AppNotification notification =
        repository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
    repository.delete(notification);
  }

  @Transactional
  public AppNotification create(
      UUID userId, String type, String title, String body, UUID referenceId, UUID conversationId) {
    return repository.save(
        new AppNotification(userId, type, title, body, referenceId, conversationId));
  }
}
