package com.example.chat_app_backend.chat.message;

import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Endpoints for sending messages and fetching history")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "Get message history", description = "Cursor pagination of messages for a conversation")
    @GetMapping("/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getHistory(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getHistory(user.getId(), conversationId, cursor, limit));
    }

    @Operation(summary = "Send HTTP Message", description = "Fallback to send message via HTTP instead of WebSocket")
    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.sendMessage(user.getId(), request));
    }
}
