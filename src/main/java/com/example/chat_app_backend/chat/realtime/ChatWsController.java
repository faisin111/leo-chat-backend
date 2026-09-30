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
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class ChatWsController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWsController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) return;
        
        UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();
        
        // Save to DB via service
        MessageResponse response = messageService.sendMessage(user.getId(), request.conversationId(), request);
        
        // Broadcast to conversation topic
        messagingTemplate.convertAndSend("/topic/conversations." + request.conversationId(), response);
        
        // Send ACK back to sender
        messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/acks", response);
    }
}
