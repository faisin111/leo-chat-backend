package com.example.chat_app_backend.chat.realtime;

import com.example.chat_app_backend.chat.message.MessageService;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import com.example.chat_app_backend.chat.realtime.dto.TypingEvent;
import com.example.chat_app_backend.chat.realtime.dto.TypingRequest;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWsController {

  private final MessageService messageService;
  private final SimpMessagingTemplate messagingTemplate;

  public ChatWsController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
    this.messageService = messageService;
    this.messagingTemplate = messagingTemplate;
  }
  
  @MessageMapping("/chat.typing")
  public void typingIndicator(
      @Payload TypingRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) return;
    UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();

    TypingEvent event = new TypingEvent(
        request.conversationId(),
        user.getId(),
        user.getUsername(),
        request.isTyping()
    );

    messagingTemplate.convertAndSend("/topic/conversations." + request.conversationId() + ".typing", event);
  }

  @MessageMapping("/chat.send")
  public void sendMessage(
      @Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
    Authentication auth = (Authentication) headerAccessor.getUser();
    if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) return;

    UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();

    // Save to DB via service
    MessageResponse response =
        messageService.sendMessage(user.getId(), request.conversationId(), request);

    // Broadcasting is now automatically handled inside messageService.sendMessage()

    // Send ACK back to sender
    messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/acks", response);
  }
}
