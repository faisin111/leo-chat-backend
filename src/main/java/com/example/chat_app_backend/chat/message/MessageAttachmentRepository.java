package com.example.chat_app_backend.chat.message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
  List<MessageAttachment> findByMessageId(UUID messageId);

  Optional<MessageAttachment> findByStorageKey(String storageKey);
}
