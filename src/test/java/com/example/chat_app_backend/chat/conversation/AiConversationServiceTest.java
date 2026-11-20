package com.example.chat_app_backend.chat.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chat_app_backend.chat.message.MessageAttachmentRepository;
import com.example.chat_app_backend.chat.message.MessageReactionRepository;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.common.exception.BusinessRuleException;
import com.example.chat_app_backend.common.exception.ForbiddenException;
import com.example.chat_app_backend.media.MediaService;
import com.example.chat_app_backend.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiConversationServiceTest {
  ConversationRepository conversations = mock(ConversationRepository.class);
  ConversationMemberRepository members = mock(ConversationMemberRepository.class);
  ConversationService service =
      new ConversationService(
          conversations,
          members,
          mock(UserRepository.class),
          mock(MessageRepository.class),
          mock(MessageAttachmentRepository.class),
          mock(MessageReactionRepository.class),
          mock(MediaService.class));
  UUID user = UUID.randomUUID();

  private Conversation conversation(UUID id, ConversationType type, UUID owner) {
    Conversation c = new Conversation();
    c.setId(id);
    c.setType(type);
    c.setCreatedBy(owner);
    return c;
  }

  @Test
  void createsSeparateAiConversationForNewClientId() {
    UUID chatId = UUID.randomUUID();
    when(conversations.findById(chatId))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(conversation(chatId, ConversationType.AI, user)));

    assertThat(service.ensureAiConversation(user, chatId)).isEqualTo(chatId);
    verify(conversations).insertAiConversationIfAbsent(chatId, "ai:" + user + ":" + chatId, user);
  }

  @Test
  void rejectsAnotherUsersAiConversation() {
    UUID chatId = UUID.randomUUID();
    when(conversations.findById(chatId))
        .thenReturn(Optional.of(conversation(chatId, ConversationType.AI, UUID.randomUUID())));

    assertThatThrownBy(() -> service.ensureAiConversation(user, chatId))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void usesNormalChatForInChatAssistantWhenMember() {
    UUID chatId = UUID.randomUUID();
    when(conversations.findById(chatId))
        .thenReturn(Optional.of(conversation(chatId, ConversationType.GROUP, UUID.randomUUID())));
    ConversationMember member = new ConversationMember();
    when(members.findByIdConversationIdAndIdUserId(chatId, user)).thenReturn(Optional.of(member));

    assertThat(service.ensureAiConversation(user, chatId)).isEqualTo(chatId);
    verify(conversations, never()).insertAiConversationIfAbsent(any(), any(), any());
  }

  @Test
  void inChatAssistantRequiresActiveMembership() {
    UUID chatId = UUID.randomUUID();
    when(conversations.findById(chatId))
        .thenReturn(Optional.of(conversation(chatId, ConversationType.DIRECT, UUID.randomUUID())));
    ConversationMember left = new ConversationMember();
    left.setLeftAt(Instant.now());
    when(members.findByIdConversationIdAndIdUserId(eq(chatId), eq(user)))
        .thenReturn(Optional.of(left));

    assertThatThrownBy(() -> service.ensureAiConversation(user, chatId))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void deleteByIdRejectsNormalChats() {
    UUID chatId = UUID.randomUUID();
    when(conversations.findById(chatId))
        .thenReturn(Optional.of(conversation(chatId, ConversationType.GROUP, user)));

    assertThatThrownBy(() -> service.deleteAiConversation(user, chatId))
        .isInstanceOf(BusinessRuleException.class);
  }
}
