package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.model.Chat;
import com.example.chat_app_backend.model.ChatType;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.request.CreateChatRequest;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.repository.ChatRepository;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.chat_app_backend.service.ChatService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chat Operations", description = "Endpoints for creating and retrieving chat rooms")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Operation(summary = "Create a Chat", description = "Creates a new chat room (one-to-one or group) and adds the requested participants.")
    @PostMapping("/create")
    public ResponseEntity<?> createChat(@RequestBody CreateChatRequest request) {
        return chatService.createChat(request);
    }

    @Operation(summary = "Get User's Chats", description = "Retrieves a list of all chat rooms the authenticated user is currently a participant in.")
    @GetMapping("/")
    public ResponseEntity<?> getUserChats() {
        return chatService.getUserChats();
    }
}
