package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.Chat;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.payload.response.UserProfileResponse;
import com.example.chat_app_backend.repository.ChatRepository;
import com.example.chat_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRepository chatRepository;

    public ResponseEntity<?> getAllUsers() {
        List<UserProfileResponse> users = userRepository.findAll().stream()
                .map(user -> new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole()))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(users);
    }

    public ResponseEntity<?> deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully by Admin."));
    }

    public ResponseEntity<?> getAllChats() {
        List<Chat> chats = chatRepository.findAll();
        return ResponseEntity.ok(chats);
    }
}
