package com.example.chat_app_backend.chat.message;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository
    extends JpaRepository<MessageReaction, MessageReaction.MessageReactionId> {
  List<MessageReaction> findByMessageId(UUID messageId);
}
