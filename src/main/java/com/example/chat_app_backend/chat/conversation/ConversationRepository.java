package com.example.chat_app_backend.chat.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  Optional<Conversation> findByDirectKey(String directKey);

  @Modifying
  @Query(
      "UPDATE Conversation c SET c.lastSeq = c.lastSeq + 1, c.lastMessageAt = :time WHERE c.id = :id")
  void incrementSeqAndTimestamp(@Param("id") UUID id, @Param("time") Instant time);

  @Query("SELECT c.lastSeq FROM Conversation c WHERE c.id = :id")
  Optional<Long> getLastSeq(@Param("id") UUID id);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember cm ON c.id = cm.id.conversationId WHERE cm.id.userId = :userId ORDER BY c.lastMessageAt DESC")
  List<Conversation> findByUserIdOrderByLastMessageAtDesc(@Param("userId") UUID userId);
}
