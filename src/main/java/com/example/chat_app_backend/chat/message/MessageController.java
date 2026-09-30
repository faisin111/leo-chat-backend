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
@Tag(name = "Messages", description = "Endpoints for sending messages and fetching history")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "Get message history")
    @GetMapping("/api/v1/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getHistory(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getHistory(user.getId(), id, beforeSeq, afterSeq, limit));
    }

    @Operation(summary = "Send HTTP Message")
    @PostMapping("/api/v1/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.sendMessage(user.getId(), id, request));
    }

    @Operation(summary = "Mark messages read")
    @PostMapping("/api/v1/conversations/{id}/read")
    public ResponseEntity<Void> readMessages(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @Valid @RequestBody com.example.chat_app_backend.chat.message.dto.ReadMessageRequest request) {
        messageService.readMessages(user.getId(), id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Single message")
    @GetMapping("/api/v1/messages/{id}")
    public ResponseEntity<MessageResponse> getMessage(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(messageService.getMessage(user.getId(), id));
    }

    @Operation(summary = "Edit message")
    @PatchMapping("/api/v1/messages/{id}")
    public ResponseEntity<MessageResponse> editMessage(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @Valid @RequestBody com.example.chat_app_backend.chat.message.dto.EditMessageRequest request) {
        return ResponseEntity.ok(messageService.editMessage(user.getId(), id, request));
    }

    @Operation(summary = "Delete message")
    @DeleteMapping("/api/v1/messages/{id}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id) {
        messageService.deleteMessage(user.getId(), id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Add reaction")
    @PostMapping("/api/v1/messages/{id}/reactions")
    public ResponseEntity<Void> addReaction(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @Valid @RequestBody com.example.chat_app_backend.chat.message.dto.AddReactionRequest request) {
        messageService.addReaction(user.getId(), id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove reaction")
    @DeleteMapping("/api/v1/messages/{id}/reactions/{emoji}")
    public ResponseEntity<Void> removeReaction(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            @PathVariable String emoji) {
        messageService.removeReaction(user.getId(), id, emoji);
        return ResponseEntity.ok().build();
    }
}
