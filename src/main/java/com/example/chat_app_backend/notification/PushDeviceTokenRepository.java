package com.example.chat_app_backend.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {
  List<PushDeviceToken> findAllByUserId(UUID userId);

  java.util.Optional<PushDeviceToken> findByToken(String token);

  void deleteByTokenAndUserId(String token, UUID userId);
}
