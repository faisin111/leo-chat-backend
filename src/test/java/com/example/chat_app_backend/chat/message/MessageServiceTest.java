package com.example.chat_app_backend.chat.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.chat_app_backend.chat.conversation.Conversation;
import com.example.chat_app_backend.chat.conversation.ConversationMemberRepository;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.exception.ForbiddenException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MessageServiceTest {
  MessageRepository messages = mock(MessageRepository.class);
  ConversationRepository conversations = mock(ConversationRepository.class);
  ConversationMemberRepository members = mock(ConversationMemberRepository.class);
  ConversationService membership = mock(ConversationService.class);
  SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
  MessageReactionRepository reactions = mock(MessageReactionRepository.class);
  MessageService service =
      new MessageService(messages, conversations, members, template, membership, reactions);
  UUID user = UUID.randomUUID();
  UUID conversation = UUID.randomUUID();
  SendMessageRequest request = new SendMessageRequest(conversation, "key", "TEXT", "hello", null);

  @Test
  void checksMembershipBeforeReturningDuplicate() {
    doThrow(new ForbiddenException("Not a member"))
        .when(membership)
        .assertActiveMember(user, conversation);
    assertThatThrownBy(() -> service.sendMessage(user, conversation, request))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(messages, template);
  }

  @Test
  void broadcastsOnlyAfterCommitAndAcknowledgesFirst() {
    when(conversations.lockById(conversation)).thenReturn(Optional.of(new Conversation()));
    when(conversations.getLastSeq(conversation)).thenReturn(Optional.of(1L));
    when(messages.saveAndFlush(any(Message.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    TransactionSynchronizationManager.initSynchronization();
    try {
      var response = service.sendMessage(user, conversation, request, "alice");
      verifyNoInteractions(template);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);
      InOrder order = inOrder(template);
      order.verify(template).convertAndSendToUser("alice", "/queue/acks", response);
      order.verify(template).convertAndSend("/topic/conversations." + conversation, response);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void rollbackDoesNotBroadcast() {
    when(conversations.lockById(conversation)).thenReturn(Optional.of(new Conversation()));
    when(conversations.getLastSeq(conversation)).thenReturn(Optional.of(1L));
    when(messages.saveAndFlush(any(Message.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.sendMessage(user, conversation, request, "alice");
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
      verifyNoInteractions(template);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
