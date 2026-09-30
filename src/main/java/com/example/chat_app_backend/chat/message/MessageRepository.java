package com.example.chat_app_backend.chat.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    
    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.seq < :cursor ORDER BY m.seq DESC")
    List<Message> findHistory(@Param("conversationId") UUID conversationId, @Param("cursor") long cursor);
    
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId, String clientMessageId);
}
