package com.example.chat_app_backend.chat.message;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository
    extends JpaRepository<MessageReaction, MessageReaction.MessageReactionId> {}
