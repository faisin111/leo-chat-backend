package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.chat.conversation.dto.CreateDirectChatRequest;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.chat_app_backend.config.openapi.StandardErrors;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Endpoints for managing chat conversations (DMs and Groups)")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Operation(summary = "Get user conversations", description = "Returns all conversations sorted by latest activity")
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(conversationService.getUserConversations(user.getId()));
    }

    @Operation(summary = "Start Direct Message", description = "Get or create a 1-to-1 conversation with another user")
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> createDirectChat(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CreateDirectChatRequest request) {
        return ResponseEntity.ok(conversationService.getOrCreateDirectChat(user.getId(), request.targetUserId()));
    }
}
