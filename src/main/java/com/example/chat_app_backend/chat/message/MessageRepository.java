package com.example.chat_app_backend.chat.message;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Message m WHERE m.id = :id")
  Optional<Message> lockById(@Param("id") UUID id);

  @Query(
      "SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.seq < :cursor ORDER BY m.seq DESC")
  List<Message> findHistory(
      @Param("conversationId") UUID conversationId,
      @Param("cursor") long cursor,
      Pageable pageable);

  @Query(
      "SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.seq > :cursor ORDER BY m.seq ASC")
  List<Message> findAfter(
      @Param("conversationId") UUID conversationId,
      @Param("cursor") long cursor,
      Pageable pageable);

  Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(
      UUID conversationId, UUID senderId, String clientMessageId);
}
