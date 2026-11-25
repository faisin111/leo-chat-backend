package com.example.chat_app_backend.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chat_app_backend.chat.ai.AiService;
import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.MessageService;
import com.example.chat_app_backend.chat.realtime.dto.CallSignalEvent;
import com.example.chat_app_backend.chat.realtime.dto.CallSignalRequest;
import com.example.chat_app_backend.notification.PushNotificationService;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.user.User;
import com.example.chat_app_backend.user.UserRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

class ChatWsControllerTest {

  private final MessageService messageService = mock(MessageService.class);
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final ConversationService conversationService = mock(ConversationService.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final SimpUserRegistry userRegistry = mock(SimpUserRegistry.class);
  private final AiService aiService = mock(AiService.class);
  private final PushNotificationService pushNotificationService =
      mock(PushNotificationService.class);
  private final PendingCallSignalService pendingCallSignalService = new PendingCallSignalService();

  private final ChatWsController controller =
      new ChatWsController(
          messageService,
          messagingTemplate,
          conversationService,
          userRepository,
          userRegistry,
          aiService,
          pushNotificationService,
          pendingCallSignalService);

  private static User userWithId(UUID id, String username) throws Exception {
    User user = new User(username, username + "@example.com", "hash", "ROLE_USER", username);
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
    return user;
  }

  private static SimpMessageHeaderAccessor accessorFor(UserDetailsImpl principal) {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
    accessor.setUser(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    return accessor;
  }

  @Test
  void sendsPushAndRingingAndReplaysBufferedOfferWhenOfflineCalleeSubscribes() throws Exception {
    UUID conversationId = UUID.randomUUID();
    UUID callId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    UUID calleeId = UUID.randomUUID();

    UserDetailsImpl callerPrincipal =
        new UserDetailsImpl(callerId, "alice", "alice@example.com", "hash", List.of());
    User callee = userWithId(calleeId, "bob");
    when(userRepository.findById(calleeId)).thenReturn(Optional.of(callee));
    when(userRegistry.getUser("bob")).thenReturn(null);
    when(pushNotificationService.sendIncomingCall(
            calleeId, callId, conversationId, callerId, "alice", true))
        .thenReturn(true);

    // 1. Web caller sends INVITE with video SDP
    controller.signalCall(
        new CallSignalRequest(
            conversationId,
            callId,
            calleeId,
            "INVITE",
            "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF",
            null,
            null,
            null),
        accessorFor(callerPrincipal));

    verify(messageService).recordCallMessage(callerId, conversationId, callId);
    ArgumentCaptor<CallSignalEvent> callerEvents = ArgumentCaptor.forClass(CallSignalEvent.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("alice"), eq("/queue/calls"), callerEvents.capture());
    assertThat(callerEvents.getValue().action()).isEqualTo("RINGING");

    // 2. Follow-up OFFER & ICE_CANDIDATE while mobile is waking up are buffered without UNAVAILABLE
    controller.signalCall(
        new CallSignalRequest(
            conversationId,
            callId,
            calleeId,
            "OFFER",
            "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF",
            null,
            null,
            null),
        accessorFor(callerPrincipal));
    controller.signalCall(
        new CallSignalRequest(
            conversationId, callId, calleeId, "ICE_CANDIDATE", null, "candidate:1", "0", 0),
        accessorFor(callerPrincipal));

    // Only the initial RINGING signal was sent to caller
    verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/calls"), any());

    // 3. Mobile accepts FCM call, connects WebSocket, and subscribes to /user/queue/calls
    UserDetailsImpl calleePrincipal =
        new UserDetailsImpl(calleeId, "bob", "bob@example.com", "hash", List.of());
    StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    subscribeAccessor.setDestination("/user/queue/calls");
    subscribeAccessor.setUser(
        new UsernamePasswordAuthenticationToken(
            calleePrincipal, null, calleePrincipal.getAuthorities()));
    Message<byte[]> msg =
        MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());
    controller.onSessionSubscribe(
        new SessionSubscribeEvent(this, msg, subscribeAccessor.getUser()));

    ArgumentCaptor<CallSignalEvent> replayedToBob = ArgumentCaptor.forClass(CallSignalEvent.class);
    verify(messagingTemplate, org.mockito.Mockito.times(2))
        .convertAndSendToUser(eq("bob"), eq("/queue/calls"), replayedToBob.capture());
    assertThat(replayedToBob.getAllValues())
        .extracting(CallSignalEvent::action)
        .containsExactly("OFFER", "ICE_CANDIDATE");
  }

  @Test
  void sendsUnavailableWhenCalleeIsOfflineAndNoPushWasDelivered() throws Exception {
    UUID conversationId = UUID.randomUUID();
    UUID callId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    UUID calleeId = UUID.randomUUID();

    UserDetailsImpl callerPrincipal =
        new UserDetailsImpl(callerId, "alice", "alice@example.com", "hash", List.of());
    User callee = userWithId(calleeId, "bob");
    when(userRepository.findById(calleeId)).thenReturn(Optional.of(callee));
    when(userRegistry.getUser("bob")).thenReturn(null);
    when(pushNotificationService.sendIncomingCall(
            calleeId, callId, conversationId, callerId, "alice", false))
        .thenReturn(false);

    controller.signalCall(
        new CallSignalRequest(conversationId, callId, calleeId, "INVITE", null, null, null, null),
        accessorFor(callerPrincipal));

    ArgumentCaptor<CallSignalEvent> callerEvent = ArgumentCaptor.forClass(CallSignalEvent.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("alice"), eq("/queue/calls"), callerEvent.capture());
    assertThat(callerEvent.getValue().action()).isEqualTo("UNAVAILABLE");
  }

  @Test
  void relaysAcceptFromMobileToOnlineCallerAndClearsPendingOnEnd() throws Exception {
    UUID conversationId = UUID.randomUUID();
    UUID callId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    UUID calleeId = UUID.randomUUID();

    UserDetailsImpl calleePrincipal =
        new UserDetailsImpl(calleeId, "bob", "bob@example.com", "hash", List.of());
    User caller = userWithId(callerId, "alice");
    when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
    when(userRegistry.getUser("alice")).thenReturn(mock(SimpUser.class));

    controller.signalCall(
        new CallSignalRequest(conversationId, callId, callerId, "ACCEPT", null, null, null, null),
        accessorFor(calleePrincipal));

    ArgumentCaptor<CallSignalEvent> deliveredToAlice =
        ArgumentCaptor.forClass(CallSignalEvent.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("alice"), eq("/queue/calls"), deliveredToAlice.capture());
    assertThat(deliveredToAlice.getValue().action()).isEqualTo("ACCEPT");
    verify(pushNotificationService, never())
        .sendIncomingCall(any(), any(), any(), any(), any(), any(Boolean.class));
  }
}
