package com.example.chat_app_backend.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {
  Page<AppNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<AppNotification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID userId, Pageable pageable);

  long countByUserIdAndReadAtIsNull(UUID userId);

  java.util.Optional<AppNotification> findByIdAndUserId(UUID id, UUID userId);

  java.util.List<AppNotification> findByUserIdAndReadAtIsNull(UUID userId);
}
