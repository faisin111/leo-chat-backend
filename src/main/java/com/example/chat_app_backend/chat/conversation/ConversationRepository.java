package com.example.chat_app_backend.chat.conversation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
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
      value =
          "INSERT INTO conversations (id, type, title, direct_key, created_by, last_seq, status, created_at) VALUES (:id, 'AI', 'LeoChat AI', :directKey, :userId, 0, 'ACTIVE', CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertAiConversationIfAbsent(
      @Param("id") UUID id, @Param("directKey") String directKey, @Param("userId") UUID userId);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember m ON c.id = m.id.conversationId WHERE c.title = 'LeoChat AI' AND c.directKey IS NULL AND m.id.userId = :userId AND m.leftAt IS NULL ORDER BY c.createdAt ASC")
  List<Conversation> findLegacyAiConversations(@Param("userId") UUID userId);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember m ON c.id = m.id.conversationId WHERE c.type = com.example.chat_app_backend.chat.conversation.ConversationType.AI AND c.createdBy = :userId AND m.id.userId = :userId AND m.leftAt IS NULL AND m.hiddenAt IS NULL ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  List<Conversation> findAiConversationsForUser(@Param("userId") UUID userId, Pageable pageable);

  @Modifying
  @Query(
      "UPDATE Conversation c SET c.lastSeq = c.lastSeq + 1, c.lastMessageAt = :time WHERE c.id = :id")
  void incrementSeqAndTimestamp(@Param("id") UUID id, @Param("time") Instant time);

  @Query("SELECT c.lastSeq FROM Conversation c WHERE c.id = :id")
  Optional<Long> getLastSeq(@Param("id") UUID id);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember cm ON c.id = cm.id.conversationId WHERE cm.id.userId = :userId AND cm.leftAt IS NULL AND cm.hiddenAt IS NULL ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  Page<Conversation> findByUserIdOrderByLastMessageAtDesc(
      @Param("userId") UUID userId, Pageable pageable);

  @Query(
      "SELECT c FROM Conversation c JOIN ConversationMember currentMember ON c.id = currentMember.id.conversationId WHERE currentMember.id.userId = :userId AND currentMember.leftAt IS NULL AND currentMember.hiddenAt IS NULL AND (:query IS NULL OR lower(coalesce(c.title, '')) LIKE lower(concat('%', :query, '%')) OR EXISTS (SELECT 1 FROM ConversationMember matchedMember JOIN User u ON u.id = matchedMember.id.userId WHERE matchedMember.id.conversationId = c.id AND matchedMember.leftAt IS NULL AND (lower(coalesce(u.username, '')) LIKE lower(concat('%', :query, '%')) OR lower(coalesce(u.displayName, '')) LIKE lower(concat('%', :query, '%'))))) ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  Page<Conversation> searchUserConversations(
      @Param("userId") UUID userId, @Param("query") String query, Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT c FROM Conversation c "
              + "LEFT JOIN ConversationMember cm ON c.id = cm.id.conversationId "
              + "LEFT JOIN User u ON u.id = cm.id.userId "
              + "WHERE (:status IS NULL OR c.status = :status) "
              + "AND (:query IS NULL OR lower(coalesce(c.title, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(u.username, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(u.displayName, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(c.directKey, '')) LIKE lower(concat('%', :query, '%')))",
      countQuery =
          "SELECT COUNT(DISTINCT c) FROM Conversation c "
              + "LEFT JOIN ConversationMember cm ON c.id = cm.id.conversationId "
              + "LEFT JOIN User u ON u.id = cm.id.userId "
              + "WHERE (:status IS NULL OR c.status = :status) "
              + "AND (:query IS NULL OR lower(coalesce(c.title, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(u.username, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(u.displayName, '')) LIKE lower(concat('%', :query, '%')) "
              + "OR lower(coalesce(c.directKey, '')) LIKE lower(concat('%', :query, '%')))")
  Page<Conversation> searchAdminConversations(
      @Param("status") String status, @Param("query") String query, Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(c.lastSeq - cm.lastReadSeq), 0) FROM Conversation c JOIN ConversationMember cm ON c.id = cm.id.conversationId WHERE cm.id.userId = :userId AND cm.leftAt IS NULL AND cm.hiddenAt IS NULL AND c.lastSeq > cm.lastReadSeq")
  Long getUnreadCountByUserId(@Param("userId") UUID userId);
}
