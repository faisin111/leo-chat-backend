package com.example.chat_app_backend.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
  boolean existsByUserIdAndProfilePictureStorageKey(UUID userId, String storageKey);
}
