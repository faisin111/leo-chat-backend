package com.example.chat_app_backend.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.security.services.UserDetailsServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class WsAuthInterceptorTest {
  JwtUtils jwt = mock(JwtUtils.class);
  UserDetailsServiceImpl users = mock(UserDetailsServiceImpl.class);
  ConversationService conversations = mock(ConversationService.class);
  WsAuthInterceptor interceptor = new WsAuthInterceptor(jwt, users, conversations);
  UserDetailsImpl user =
      new UserDetailsImpl(UUID.randomUUID(), "alice", "a@test.com", "hash", List.of());

  Message<byte[]> frame(StompCommand command, String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    if (command != StompCommand.CONNECT)
      accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Test
  void rejectsMissingAndInvalidTokens() {
    assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, ""), null))
        .isInstanceOf(MessagingException.class);
    Message<byte[]> message = frame(StompCommand.CONNECT, "");
    StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)
        .setNativeHeader("Authorization", "Bearer invalid");
    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessagingException.class);
  }

  @Test
  void authenticatesValidAccessToken() {
    when(jwt.validateJwtToken("valid")).thenReturn(true);
    when(jwt.getUserNameFromJwtToken("valid")).thenReturn("alice");
    when(users.loadUserByUsername("alice")).thenReturn(user);
    Message<byte[]> message = frame(StompCommand.CONNECT, "");
    StompHeaderAccessor accessor =
        StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    accessor.setNativeHeader("Authorization", "Bearer valid");
    interceptor.preSend(message, null);
    assertThat(accessor.getUser().getName()).isEqualTo("alice");
  }

  @Test
  void rejectsNonMemberAndBrokerInjection() {
    UUID id = UUID.randomUUID();
    doThrow(new ForbiddenException("Not a member"))
        .when(conversations)
        .assertActiveMember(user.getId(), id);
    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, "/topic/conversations." + id), null))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(
            () -> interceptor.preSend(frame(StompCommand.SEND, "/topic/conversations." + id), null))
        .isInstanceOf(MessagingException.class);
    assertThatThrownBy(
            () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/queue/acks"), null))
        .isInstanceOf(MessagingException.class);
  }

  @Test
  void permitsOnlyOwnQueuesAndMemberTopics() {
    UUID id = UUID.randomUUID();
    interceptor.preSend(
        frame(StompCommand.SUBSCRIBE, "/topic/conversations." + id + ".typing"), null);
    verify(conversations).assertActiveMember(user.getId(), id);
    interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/acks"), null);
  }
}
