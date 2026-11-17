package com.example.chat_app_backend.chat.message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
  List<MessageAttachment> findByMessageId(UUID messageId);

  Optional<MessageAttachment> findByStorageKey(String storageKey);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "DELETE FROM MessageAttachment a WHERE a.messageId IN (SELECT m.id FROM Message m WHERE m.conversationId = :conversationId)")
  void deleteByConversationId(
      @org.springframework.data.repository.query.Param("conversationId") UUID conversationId);
}
