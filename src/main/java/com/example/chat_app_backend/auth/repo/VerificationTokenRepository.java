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
  @org.springframework.data.jpa.repository.Query("DELETE FROM VerificationToken v WHERE v.userId = :userId AND v.type = :type")
  void deleteByUserIdAndType(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("type") String type);
}
