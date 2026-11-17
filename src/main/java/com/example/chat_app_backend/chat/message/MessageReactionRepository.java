package com.example.chat_app_backend.chat.message;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository
    extends JpaRepository<MessageReaction, MessageReaction.MessageReactionId> {
  List<MessageReaction> findByMessageId(UUID messageId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "DELETE FROM MessageReaction r WHERE r.messageId IN (SELECT m.id FROM Message m WHERE m.conversationId = :conversationId)")
  void deleteByConversationId(
      @org.springframework.data.repository.query.Param("conversationId") UUID conversationId);
}
