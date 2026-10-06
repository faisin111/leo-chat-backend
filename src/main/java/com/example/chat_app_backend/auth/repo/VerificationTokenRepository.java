package com.example.chat_app_backend.auth.repo;

import com.example.chat_app_backend.auth.model.VerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
  Optional<VerificationToken> findByTokenAndType(String token, String type);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  void deleteByUserIdAndType(UUID userId, String type);
}
