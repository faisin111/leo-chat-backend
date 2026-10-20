package com.example.chat_app_backend.repository;

import com.example.chat_app_backend.model.RefreshSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
  Optional<RefreshSession> findByTokenHash(String tokenHash);
  List<RefreshSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
  List<RefreshSession> findByUserIdAndLogoutAtIsNull(UUID userId);
}
