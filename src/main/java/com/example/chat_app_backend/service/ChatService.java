package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.Chat;
import com.example.chat_app_backend.model.ChatType;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.request.CreateChatRequest;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.repository.ChatRepository;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository;

    public ResponseEntity<?> createChat(CreateChatRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> currentUserOpt = userRepository.findById(userDetails.getId());
        
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse(AppConstants.ERR_USER_NOT_FOUND));
        }

        Set<User> participants = new HashSet<>();
        participants.add(currentUserOpt.get());

        if (request.getParticipantIds() != null) {
            for (UUID id : request.getParticipantIds()) {
                userRepository.findById(id).ifPresent(participants::add);
            }
        }

        Chat chat = new Chat();
        chat.setChatType(ChatType.valueOf(request.getChatType()));
        chat.setChatName(request.getChatName());
        chat.setParticipants(participants);

        chatRepository.save(chat);

        return ResponseEntity.ok(chat);
    }

    public ResponseEntity<?> getUserChats() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Chat> chats = chatRepository.findByParticipants_Id(userDetails.getId());
        return ResponseEntity.ok(chats);
    }
}
