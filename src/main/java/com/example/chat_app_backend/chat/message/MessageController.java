package com.example.chat_app_backend.chat.message;

import com.example.chat_app_backend.chat.message.dto.AddReactionRequest;
import com.example.chat_app_backend.chat.message.dto.EditMessageRequest;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.ReadMessageRequest;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.payload.response.CursorPageResponse;
import com.example.chat_app_backend.payload.response.MessagePageResponse;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@RestController
@Tag(name = "Messages", description = "Endpoints for sending messages and fetching history")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "cookieAuth")
@StandardErrors
public class MessageController {

  private final MessageService messageService;

  public MessageController(MessageService messageService) {
    this.messageService = messageService;
  }

  @Operation(
      summary = "Get message history",
      description = "Get message history for an authorized conversation member.",
      operationId = "messages_history")
  @ApiResponse(
      responseCode = "200",
      description = "Successful response",
      content =
          @Content(
              examples =
                  @ExampleObject(value = "{\"items\":[],\"hasMore\":false,\"nextCursor\":null}"),
              schema = @Schema(implementation = MessagePageResponse.class)))
  @GetMapping("/api/v1/conversations/{id}/messages")
  public ResponseEntity<CursorPageResponse<MessageResponse>> getHistory(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Parameter(description = "Exclusive upper sequence cursor; returns descending history")
          @RequestParam(required = false)
          Long beforeSeq,
      @Parameter(description = "Exclusive lower sequence cursor; returns ascending sync history")
          @RequestParam(required = false)
          Long afterSeq,
      @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "50")
          int limit) {
    return ResponseEntity.ok(
        messageService.getHistory(user.getId(), id, beforeSeq, afterSeq, limit));
  }

  @Operation(
      summary = "Send HTTP Message",
      description = "Send HTTP Message for an authorized conversation member.",
      operationId = "messages_send")
  @ApiResponse(
      responseCode = "200",
      description = "Persisted message",
      content =
          @Content(
              schema = @Schema(implementation = MessageResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\"id\":\"ec27673c-89a3-448a-9f27-e7f1ea0a071c\",\"seq\":1,\"clientMessageId\":\"msg-1\",\"type\":\"TEXT\",\"content\":\"Hello\"}")))
  @PostMapping("/api/v1/conversations/{id}/messages")
  public ResponseEntity<MessageResponse> sendMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest request) {
    return ResponseEntity.ok(messageService.sendMessage(user.getId(), id, request));
  }

  @Operation(
      summary = "Mark messages read",
      description = "Mark messages read for an authorized conversation member.",
      operationId = "messages_read")
  @ApiResponse(
      responseCode = "200",
      description = "Request completed; response body is empty",
      content = @Content)
  @PostMapping("/api/v1/conversations/{id}/read")
  public ResponseEntity<Void> readMessages(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Valid @RequestBody ReadMessageRequest request) {
    messageService.readMessages(user.getId(), id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "Get message",
      description = "Get message for an authorized conversation member.",
      operationId = "messages_get")
  @ApiResponse(
      responseCode = "200",
      description = "Persisted message",
      content =
          @Content(
              schema = @Schema(implementation = MessageResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\"id\":\"ec27673c-89a3-448a-9f27-e7f1ea0a071c\",\"seq\":1,\"clientMessageId\":\"msg-1\",\"type\":\"TEXT\",\"content\":\"Hello\"}")))
  @GetMapping("/api/v1/messages/{id}")
  public ResponseEntity<MessageResponse> getMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable
          UUID id) {
    return ResponseEntity.ok(messageService.getMessage(user.getId(), id));
  }

  @Operation(
      summary = "Edit message",
      description = "Edit message for an authorized conversation member.",
      operationId = "messages_edit")
  @ApiResponse(
      responseCode = "200",
      description = "Persisted message",
      content =
          @Content(
              schema = @Schema(implementation = MessageResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\"id\":\"ec27673c-89a3-448a-9f27-e7f1ea0a071c\",\"seq\":1,\"clientMessageId\":\"msg-1\",\"type\":\"TEXT\",\"content\":\"Hello\"}")))
  @PatchMapping("/api/v1/messages/{id}")
  public ResponseEntity<MessageResponse> editMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Valid @RequestBody EditMessageRequest request) {
    return ResponseEntity.ok(messageService.editMessage(user.getId(), id, request));
  }

  @Operation(
      summary = "Delete message",
      description = "Delete message for an authorized conversation member.",
      operationId = "messages_delete")
  @ApiResponse(
      responseCode = "200",
      description = "Request completed; response body is empty",
      content = @Content)
  @DeleteMapping("/api/v1/messages/{id}")
  public ResponseEntity<Void> deleteMessage(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable
          UUID id) {
    messageService.deleteMessage(user.getId(), id);
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "Add reaction",
      description = "Add reaction for an authorized conversation member.",
      operationId = "messages_add_reaction")
  @ApiResponse(
      responseCode = "200",
      description = "Request completed; response body is empty",
      content = @Content)
  @PostMapping("/api/v1/messages/{id}/reactions")
  public ResponseEntity<Void> addReaction(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Valid @RequestBody AddReactionRequest request) {
    messageService.addReaction(user.getId(), id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "Remove reaction",
      description = "Remove reaction for an authorized conversation member.",
      operationId = "messages_remove_reaction")
  @ApiResponse(
      responseCode = "200",
      description = "Request completed; response body is empty",
      content = @Content)
  @DeleteMapping("/api/v1/messages/{id}/reactions/{emoji}")
  public ResponseEntity<Void> removeReaction(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Parameter(description = "Conversation or message ID", required = true) @PathVariable UUID id,
      @Parameter(description = "Reaction emoji", required = true) @PathVariable String emoji) {
    messageService.removeReaction(user.getId(), id, emoji);
    return ResponseEntity.ok().build();
  }
}
