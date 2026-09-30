package com.example.chat_app_backend.auth.repo;

import com.example.chat_app_backend.auth.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenAndType(String token, String type);
    void deleteByUserIdAndType(UUID userId, String type);
}
