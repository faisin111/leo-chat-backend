package com.example.chat_app_backend.chat.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository
    extends JpaRepository<ConversationMember, ConversationMemberId> {
  List<ConversationMember> findByIdConversationId(UUID conversationId);

  List<ConversationMember> findByIdUserId(UUID userId);

  Optional<ConversationMember> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);
}
