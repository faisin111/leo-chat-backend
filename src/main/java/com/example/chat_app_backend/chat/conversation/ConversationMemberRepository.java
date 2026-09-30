package com.example.chat_app_backend.chat.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    List<ConversationMember> findByIdConversationId(UUID conversationId);
    Optional<ConversationMember> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);
}
