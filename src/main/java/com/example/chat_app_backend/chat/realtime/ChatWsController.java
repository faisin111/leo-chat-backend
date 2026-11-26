package com.example.chat_app_backend.chat.realtime;

import com.example.chat_app_backend.chat.ai.AiService;
import com.example.chat_app_backend.chat.ai.dto.AiChatRequest;
import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.MessageService;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.ReadMessageRequest;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.chat.realtime.dto.CallSignalEvent;
import com.example.chat_app_backend.chat.realtime.dto.CallSignalRequest;
import com.example.chat_app_backend.chat.realtime.dto.TypingEvent;
import com.example.chat_app_backend.chat.realtime.dto.TypingRequest;
import com.example.chat_app_backend.chat.realtime.dto.WsReadRequest;
import com.example.chat_app_backend.common.exception.BusinessRuleException;
import com.example.chat_app_backend.common.exception.ForbiddenException;
import com.example.chat_app_backend.common.exception.NotFoundException;
import com.example.chat_app_backend.notification.PushNotificationService;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.user.UserRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Controller
public class ChatWsController {

  private final ConversationService conversationService;
  private final MessageService messageService;
  private final SimpMessagingTemplate messagingTemplate;
  private final SimpUserRegistry userRegistry;
  private final UserRepository userRepository;
  private final AiService aiService;
  private final PushNotificationService pushNotificationService;
  private final PendingCallSignalService pendingCallSignalService;
  private final ActiveCallService activeCallService;

  public ChatWsController(
      MessageService messageService,
      SimpMessagingTemplate messagingTemplate,
      ConversationService conversationService,
      UserRepository userRepository,
      SimpUserRegistry userRegistry,
      AiService aiService,
      PushNotificationService pushNotificationService,
      PendingCallSignalService pendingCallSignalService,
      ActiveCallService activeCallService) {
    this.conversationService = conversationService;
    this.messageService = messageService;
    this.messagingTemplate = messagingTemplate;
    this.userRepository = userRepository;
    this.userRegistry = userRegistry;
    this.aiService = aiService;
    this.pushNotificationService = pushNotificationService;
    this.pendingCallSignalService = pendingCallSignalService;
    this.activeCallService = activeCallService;
  }

  @MessageMapping("/chat.typing")
  public void typingIndicator(
      @Valid @Payload TypingRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl))
      throw new MessagingException("Unauthorized");
    UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();

    conversationService.assertActiveMember(user.getId(), request.conversationId());
    TypingEvent event =
        new TypingEvent(
            request.conversationId(), user.getId(), user.getUsername(), request.isTyping());

    messagingTemplate.convertAndSend(
        "/topic/conversations." + request.conversationId() + ".typing", event);
  }

  @MessageMapping("/chat.read")
  public void readMessages(
      @Valid @Payload WsReadRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user))
      throw new MessagingException("Unauthorized");
    messageService.readMessages(
        user.getId(), request.conversationId(), new ReadMessageRequest(request.upToSeq()));
  }

  @MessageExceptionHandler
  @SendToUser(value = "/queue/errors", broadcast = false)
  public Map<String, String> handleError(Exception exception) {
    String code =
        exception instanceof ForbiddenException
            ? "FORBIDDEN"
            : exception instanceof NotFoundException
                ? "NOT_FOUND"
                : exception instanceof BusinessRuleException
                    ? "BUSINESS_RULE"
                    : exception instanceof MethodArgumentNotValidException
                        ? "VALIDATION_ERROR"
                        : "INTERNAL_ERROR";
    return Map.of("code", code, "message", "Request failed");
  }

  @MessageMapping("/chat.send")
  public void sendMessage(
      @Valid @Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl))
      throw new MessagingException("Unauthorized");

    UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();

    // Save to DB via service
    MessageResponse response =
        messageService.sendMessage(
            user.getId(), request.conversationId(), request, user.getUsername());

    // Broadcasting is now automatically handled inside messageService.sendMessage()

    // Send ACK back to sender
    // The service schedules the ACK before the broadcast, after the transaction commits.
  }

  /** Relay WebRTC signaling; the server does not handle audio/video bytes. */
  @MessageMapping("/call.signal")
  public void signalCall(
      @Valid @Payload CallSignalRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user))
      throw new MessagingException("Unauthorized");

    conversationService.assertActiveMember(user.getId(), request.conversationId());
    if (user.getId().equals(request.targetUserId()))
      throw new BusinessRuleException("Cannot call yourself");
    var target =
        userRepository
            .findById(request.targetUserId())
            .orElseThrow(() -> new NotFoundException("Target user not found"));
    conversationService.assertActiveMember(target.getId(), request.conversationId());

    String action = request.action().trim().toUpperCase(java.util.Locale.ROOT);
    if (!java.util.Set.of(
            "INVITE",
            "OFFER",
            "ACCEPT",
            "READY",
            "ANSWER",
            "ICE_CANDIDATE",
            "REJECT",
            "BUSY",
            "END")
        .contains(action)) {
      throw new BusinessRuleException("Unsupported call signal action");
    }
    boolean pushSent = false;
    if ("INVITE".equals(action)) {
      messageService.recordCallMessage(user.getId(), request.conversationId(), request.callId());
      boolean isVideo = request.sdp() != null && request.sdp().contains("m=video");
      activeCallService.start(
          new ActiveCallService.CallState(
              request.callId(),
              request.conversationId(),
              user.getId(),
              target.getId(),
              user.getUsername(),
              isVideo,
              false));
      pushSent =
          pushNotificationService.sendIncomingCall(
              target.getId(),
              request.callId(),
              request.conversationId(),
              user.getId(),
              user.getUsername(),
              isVideo);
    } else if ("ACCEPT".equals(action) || "READY".equals(action) || "ANSWER".equals(action)) {
      activeCallService.markAnswered(request.callId());
    } else if ("END".equals(action)) {
      ActiveCallService.CallState missed = activeCallService.takeMissed(request.callId());
      if (missed != null && user.getId().equals(missed.callerId())) {
        pushNotificationService.sendMissedCall(
            missed.calleeId(),
            missed.callId(),
            missed.conversationId(),
            missed.callerId(),
            missed.callerName(),
            missed.video());
      }
      pendingCallSignalService.clearCall(request.callId());
    } else if ("REJECT".equals(action) || "BUSY".equals(action)) {
      activeCallService.finish(request.callId());
      pendingCallSignalService.clearCall(request.callId());
    }

    CallSignalEvent event =
        new CallSignalEvent(
            request.conversationId(),
            request.callId(),
            user.getId(),
            user.getUsername(),
            target.getId(),
            action,
            request.sdp(),
            request.candidate(),
            request.sdpMid(),
            request.sdpMLineIndex());
    if (userRegistry.getUser(target.getUsername()) == null) {
      if ("INVITE".equals(action) && pushSent) {
        pendingCallSignalService.recordInvite(target.getUsername(), event);
        messagingTemplate.convertAndSendToUser(
            user.getUsername(),
            "/queue/calls",
            new CallSignalEvent(
                request.conversationId(),
                request.callId(),
                user.getId(),
                user.getUsername(),
                target.getId(),
                "RINGING",
                null,
                null,
                null,
                null));
        return;
      } else if (("OFFER".equals(action) || "ICE_CANDIDATE".equals(action))
          && pendingCallSignalService.bufferIfPending(target.getUsername(), event)) {
        return;
      } else if ("END".equals(action) || "REJECT".equals(action) || "BUSY".equals(action)) {
        return;
      }

      messagingTemplate.convertAndSendToUser(
          user.getUsername(),
          "/queue/calls",
          new CallSignalEvent(
              request.conversationId(),
              request.callId(),
              user.getId(),
              user.getUsername(),
              target.getId(),
              "UNAVAILABLE",
              null,
              null,
              null,
              null));
      return;
    }
    messagingTemplate.convertAndSendToUser(target.getUsername(), "/queue/calls", event);
    messagingTemplate.convertAndSendToUser(
        user.getUsername(),
        "/queue/calls",
        new CallSignalEvent(
            request.conversationId(),
            request.callId(),
            user.getId(),
            user.getUsername(),
            target.getId(),
            "DELIVERED",
            null,
            null,
            null,
            null));
  }

  /** Flushes queued WebRTC OFFER/ICE signals when a push-woken mobile user subscribes. */
  @EventListener
  @Order(Ordered.LOWEST_PRECEDENCE)
  public void onSessionSubscribe(SessionSubscribeEvent subscribeEvent) {
    SimpMessageHeaderAccessor accessor =
        SimpMessageHeaderAccessor.wrap(subscribeEvent.getMessage());
    String destination = accessor.getDestination();
    Principal principal = subscribeEvent.getUser();
    if (principal == null || !"/user/queue/calls".equals(destination)) {
      return;
    }

    String username = principal.getName();
    List<CallSignalEvent> pendingSignals = pendingCallSignalService.drainSignals(username);
    if (pendingSignals.isEmpty()) {
      return;
    }

    Set<UUID> deliveredCalls = new HashSet<>();
    for (CallSignalEvent pending : pendingSignals) {
      messagingTemplate.convertAndSendToUser(username, "/queue/calls", pending);
      if (deliveredCalls.add(pending.callId()) && pending.fromUsername() != null) {
        messagingTemplate.convertAndSendToUser(
            pending.fromUsername(),
            "/queue/calls",
            new CallSignalEvent(
                pending.conversationId(),
                pending.callId(),
                pending.fromUserId(),
                pending.fromUsername(),
                pending.targetUserId(),
                "DELIVERED",
                null,
                null,
                null,
                null));
      }
    }
  }

  @MessageMapping("/ai.chat")
  public void chatWithAi(
      @Valid @Payload AiChatRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user))
      throw new MessagingException("Unauthorized");
    aiService.chat(user.getId(), request);
  }
}
