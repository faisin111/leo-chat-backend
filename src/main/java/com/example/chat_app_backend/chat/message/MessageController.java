package com.example.chat_app_backend.chat.message;

import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;


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
  public ResponseEntity<
          com.example.chat_app_backend.payload.response.CursorPageResponse<MessageResponse>>
      getHistory(
          @AuthenticationPrincipal UserDetailsImpl user,
          @PathVariable UUID id,
          @RequestParam(required = false) Long beforeSeq,
          @RequestParam(required = false) Long afterSeq,
          @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(
        messageService.getHistory(user.getId(), id, beforeSeq, afterSeq, limit));
  }

  @Operation(summary = "Send HTTP Message")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/api/v1/conversations/{id}/messages")
  public ResponseEntity<MessageResponse> sendMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest request) {
    return ResponseEntity.ok(messageService.sendMessage(user.getId(), id, request));
  }

  @Operation(summary = "Mark messages read")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/api/v1/conversations/{id}/read")
  public ResponseEntity<Void> readMessages(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable UUID id,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.message.dto.ReadMessageRequest request) {
    messageService.readMessages(user.getId(), id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Single message")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @GetMapping("/api/v1/messages/{id}")
  public ResponseEntity<MessageResponse> getMessage(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
    return ResponseEntity.ok(messageService.getMessage(user.getId(), id));
  }

  @Operation(summary = "Edit message")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PatchMapping("/api/v1/messages/{id}")
  public ResponseEntity<MessageResponse> editMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable UUID id,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.message.dto.EditMessageRequest request) {
    return ResponseEntity.ok(messageService.editMessage(user.getId(), id, request));
  }

  @Operation(summary = "Delete message")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @DeleteMapping("/api/v1/messages/{id}")
  public ResponseEntity<Void> deleteMessage(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
    messageService.deleteMessage(user.getId(), id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Add reaction")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/api/v1/messages/{id}/reactions")
  public ResponseEntity<Void> addReaction(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable UUID id,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.message.dto.AddReactionRequest request) {
    messageService.addReaction(user.getId(), id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Remove reaction")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @DeleteMapping("/api/v1/messages/{id}/reactions/{emoji}")
  public ResponseEntity<Void> removeReaction(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable UUID id,
      @PathVariable String emoji) {
    messageService.removeReaction(user.getId(), id, emoji);
    return ResponseEntity.ok().build();
  }
}
