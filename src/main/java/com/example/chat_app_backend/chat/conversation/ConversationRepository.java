package com.example.chat_app_backend.chat.conversation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Conversation c WHERE c.id = :id")
  Optional<Conversation> lockById(@Param("id") UUID id);

  Optional<Conversation> findByDirectKey(String directKey);

  @Modifying
  @Query(
      "UPDATE Conversation c SET c.lastSeq = c.lastSeq + 1, c.lastMessageAt = :time WHERE c.id = :id")
  void incrementSeqAndTimestamp(@Param("id") UUID id, @Param("time") Instant time);

  @Query("SELECT c.lastSeq FROM Conversation c WHERE c.id = :id")
  Optional<Long> getLastSeq(@Param("id") UUID id);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember cm ON c.id = cm.id.conversationId WHERE cm.id.userId = :userId ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  Page<Conversation> findByUserIdOrderByLastMessageAtDesc(
      @Param("userId") UUID userId, Pageable pageable);

  @Query(
      "SELECT DISTINCT c FROM Conversation c JOIN ConversationMember currentMember ON c.id = currentMember.id.conversationId JOIN ConversationMember matchedMember ON c.id = matchedMember.id.conversationId JOIN User u ON u.id = matchedMember.id.userId WHERE currentMember.id.userId = :userId AND (:query IS NULL OR lower(coalesce(c.title, '')) LIKE lower(concat('%', :query, '%')) OR lower(coalesce(u.username, '')) LIKE lower(concat('%', :query, '%')) OR lower(coalesce(u.displayName, '')) LIKE lower(concat('%', :query, '%'))) ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  Page<Conversation> searchUserConversations(
      @Param("userId") UUID userId, @Param("query") String query, Pageable pageable);

  @Query(
      "select c from Conversation c where (:status is null or c.status = :status) and (:query is null or lower(coalesce(c.title, '')) like lower(concat('%', :query, '%')) or lower(coalesce(c.directKey, '')) like lower(concat('%', :query, '%')))")
  Page<Conversation> searchAdminConversations(
      @Param("status") String status, @Param("query") String query, Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(c.lastSeq - cm.lastReadSeq), 0) FROM Conversation c JOIN ConversationMember cm ON c.id = cm.id.conversationId WHERE cm.id.userId = :userId AND c.lastSeq > cm.lastReadSeq")
  Long getUnreadCountByUserId(@Param("userId") UUID userId);
}
