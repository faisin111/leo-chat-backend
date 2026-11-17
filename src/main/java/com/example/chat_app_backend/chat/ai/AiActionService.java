package com.example.chat_app_backend.chat.ai;

import com.example.chat_app_backend.chat.ai.dto.AiActionRequest;
import com.example.chat_app_backend.chat.ai.dto.AiActionResponse;
import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.MessageService;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.chat.realtime.dto.CallSignalEvent;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.model.UserBlock;
import com.example.chat_app_backend.repository.UserBlockRepository;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.service.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiActionService {

  private final UserService userService;
  private final ConversationService conversationService;
  private final MessageService messageService;
  private final UserRepository userRepository;
  private final UserBlockRepository userBlockRepository;
  private final SimpMessagingTemplate messagingTemplate;

  public AiActionService(
      UserService userService,
      ConversationService conversationService,
      MessageService messageService,
      UserRepository userRepository,
      UserBlockRepository userBlockRepository,
      SimpMessagingTemplate messagingTemplate) {
    this.userService = userService;
    this.conversationService = conversationService;
    this.messageService = messageService;
    this.userRepository = userRepository;
    this.userBlockRepository = userBlockRepository;
    this.messagingTemplate = messagingTemplate;
  }

  public AiActionResponse execute(UUID userId, AiActionRequest request) {
    String action = request.action().strip().toUpperCase(Locale.ROOT);
    return switch (action) {
      case "SEARCH_USERS" -> searchUsers(request);
      case "LIST_CONVERSATIONS" -> listConversations(userId, request);
      case "GET_BLOCKED_USERS" -> blockedUsers(userId);
      case "GET_USER" -> getUser(request);
      case "SEND_MESSAGE" -> sendMessage(userId, request);
      case "START_CALL" -> startCall(userId, request);
      default -> throw new BusinessRuleException("Unsupported AI action");
    };
  }

  private AiActionResponse searchUsers(AiActionRequest request) {
    if (request.query() == null || request.query().strip().length() < 2)
      throw new BusinessRuleException("query must contain at least 2 characters");
    Object data = userService.searchUsers(request.query(), 0, 20).getBody();
    return done("SEARCH_USERS", data);
  }

  private AiActionResponse listConversations(UUID userId, AiActionRequest request) {
    Object data = conversationService.getUserConversations(userId, null, 50, request.query());
    return done("LIST_CONVERSATIONS", data);
  }

  private AiActionResponse blockedUsers(UUID userId) {
    List<Map<String, Object>> data =
        userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(userId).stream()
            .map(
                block ->
                    Map.<String, Object>of(
                        "userId", block.getBlockedId(), "blockedAt", block.getCreatedAt()))
            .toList();
    return done("GET_BLOCKED_USERS", data);
  }

  private AiActionResponse getUser(AiActionRequest request) {
    if (request.targetUserId() == null) throw new BusinessRuleException("targetUserId is required");
    return done("GET_USER", userService.getUserById(request.targetUserId()).getBody());
  }

  private AiActionResponse sendMessage(UUID userId, AiActionRequest request) {
    requireConversation(request);
    if (request.message() == null || request.message().isBlank())
      throw new BusinessRuleException("message is required");
    if (!request.confirmed())
      return confirm("SEND_MESSAGE", "Confirm sending this message", request.message());
    MessageResponse sent =
        messageService.sendMessage(
            userId,
            request.conversationId(),
            new SendMessageRequest(
                request.conversationId(), "ai-action-" + UUID.randomUUID(), "TEXT", request.message(), null));
    return done("SEND_MESSAGE", sent);
  }

  private AiActionResponse startCall(UUID userId, AiActionRequest request) {
    requireConversation(request);
    if (request.targetUserId() == null) throw new BusinessRuleException("targetUserId is required");
    if (userId.equals(request.targetUserId())) throw new BusinessRuleException("Cannot call yourself");
    conversationService.assertActiveMember(userId, request.conversationId());
    conversationService.assertActiveMember(request.targetUserId(), request.conversationId());
    var target =
        userRepository
            .findById(request.targetUserId())
            .orElseThrow(() -> new NotFoundException("Target user not found"));
    if (!request.confirmed())
      return confirm("START_CALL", "Confirm starting a call with " + target.getUsername(), null);
    UUID callId = UUID.randomUUID();
    messagingTemplate.convertAndSendToUser(
        target.getUsername(),
        "/queue/calls",
        new CallSignalEvent(
            request.conversationId(), callId, userId, null, target.getId(), "INVITE", null, null, null, null));
    return done("START_CALL", Map.of("callId", callId, "targetUserId", target.getId()));
  }

  private static void requireConversation(AiActionRequest request) {
    if (request.conversationId() == null) throw new BusinessRuleException("conversationId is required");
  }

  private static AiActionResponse confirm(String action, String message, Object data) {
    return new AiActionResponse(action, false, true, message, data);
  }

  private static AiActionResponse done(String action, Object data) {
    return new AiActionResponse(action, true, false, "Action completed", data);
  }
}
