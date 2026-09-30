package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.model.Chat;
import com.example.chat_app_backend.model.Message;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.request.ChatMessagePayload;
import com.example.chat_app_backend.repository.ChatRepository;
import com.example.chat_app_backend.repository.MessageRepository;
import com.example.chat_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;

@Controller
public class MessageController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository;

    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatMessagePayload chatMessage) {
        Optional<Chat> chatOpt = chatRepository.findById(chatMessage.getChatId());
        Optional<User> senderOpt = userRepository.findById(chatMessage.getSenderId());

        if (chatOpt.isPresent() && senderOpt.isPresent()) {
            Message message = new Message();
            message.setChat(chatOpt.get());
            message.setSender(senderOpt.get());
            message.setContent(chatMessage.getContent());
            message.setMessageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT");
            message.setStatus("SENT");

            Message savedMessage = messageRepository.save(message);

            // Send to all participants of this chat
            for (User participant : chatOpt.get().getParticipants()) {
                messagingTemplate.convertAndSendToUser(
                        participant.getUsername(), "/queue/messages",
                        savedMessage
                );
            }
        }
    }
}
