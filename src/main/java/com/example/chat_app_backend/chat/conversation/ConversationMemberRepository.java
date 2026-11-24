package com.example.chat_app_backend.chat.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository
    extends JpaRepository<ConversationMember, ConversationMemberId> {
  @Modifying
  @Query(
      "UPDATE ConversationMember m SET m.lastReadSeq = :seq WHERE m.id.conversationId = :conversationId AND m.id.userId = :userId AND m.lastReadSeq < :seq AND m.leftAt IS NULL")
  int advanceReadSeq(
      @Param("conversationId") UUID conversationId,
      @Param("userId") UUID userId,
      @Param("seq") long seq);

  List<ConversationMember> findByIdConversationId(UUID conversationId);

  List<ConversationMember> findByIdUserId(UUID userId);

  Optional<ConversationMember> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

  long countByIdConversationIdAndLeftAtIsNull(UUID conversationId);

  void deleteByIdConversationId(UUID conversationId);

  @Modifying
  @Query(
      "UPDATE ConversationMember m SET m.hiddenAt = :hiddenAt, m.historyHiddenAt = :hiddenAt WHERE m.id.userId = :userId AND m.leftAt IS NULL")
  int setHiddenForUser(@Param("userId") UUID userId, @Param("hiddenAt") java.time.Instant hiddenAt);

  @Modifying
  @Query(
      "UPDATE ConversationMember m SET m.hiddenAt = :hiddenAt, m.historyHiddenAt = :hiddenAt WHERE m.id.userId = :userId AND m.leftAt IS NULL AND EXISTS (SELECT target.id.conversationId FROM ConversationMember target WHERE target.id.conversationId = m.id.conversationId AND target.id.userId = :targetUserId AND target.leftAt IS NULL)")
  int setHiddenForUserConversations(
      @Param("userId") UUID userId,
      @Param("targetUserId") UUID targetUserId,
      @Param("hiddenAt") java.time.Instant hiddenAt);
}
