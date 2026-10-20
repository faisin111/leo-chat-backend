package com.example.chat_app_backend.chat.realtime;

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
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWsController {

  private final ConversationService conversationService;
  private final MessageService messageService;
  private final SimpMessagingTemplate messagingTemplate;
  private final UserRepository userRepository;

  public ChatWsController(
      MessageService messageService,
      SimpMessagingTemplate messagingTemplate,
      ConversationService conversationService,
      UserRepository userRepository) {
    this.conversationService = conversationService;
    this.messageService = messageService;
    this.messagingTemplate = messagingTemplate;
    this.userRepository = userRepository;
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
    if (!java.util.Set.of("OFFER", "ANSWER", "ICE_CANDIDATE", "REJECT", "BUSY", "END")
        .contains(action)) {
      throw new BusinessRuleException("Unsupported call signal action");
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
    messagingTemplate.convertAndSendToUser(target.getUsername(), "/queue/calls", event);
  }
}
