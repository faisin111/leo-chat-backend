package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.chat.conversation.dto.CreateDirectChatRequest;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(
    name = "Conversations",
    description = "Endpoints for managing chat conversations (DMs and Groups)")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class ConversationController {

  private final ConversationService conversationService;

  public ConversationController(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @Operation(
      summary = "Get user conversations",
      description = "Returns all conversations sorted by latest activity")
  @GetMapping
  public ResponseEntity<
          com.example.chat_app_backend.payload.response.CursorPageResponse<ConversationResponse>>
      getConversations(
          @AuthenticationPrincipal UserDetailsImpl user,
          @RequestParam(required = false) Long cursor,
          @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(conversationService.getUserConversations(user.getId(), cursor, limit));
  }

  @Operation(
      summary = "Start Direct Message",
      description = "Get or create a 1-to-1 conversation with another user")
  @PostMapping("/direct")
  public ResponseEntity<ConversationResponse> createDirectChat(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Valid @RequestBody CreateDirectChatRequest request) {
    return ResponseEntity.ok(
        conversationService.getOrCreateDirectChat(user.getId(), request.targetUserId()));
  }

  @Operation(summary = "Get conversation details")
  @GetMapping("/{id}")
  public ResponseEntity<ConversationResponse> getConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    return ResponseEntity.ok(conversationService.getConversation(user.getId(), id));
  }

  @Operation(summary = "Get unread count")
  @GetMapping("/unread-count")
  public ResponseEntity<com.example.chat_app_backend.chat.conversation.dto.UnreadCountResponse>
      getUnreadCount(@AuthenticationPrincipal UserDetailsImpl user) {
    return ResponseEntity.ok(conversationService.getUnreadCount(user.getId()));
  }

  @Operation(summary = "Create group chat")
  @PostMapping("/group")
  public ResponseEntity<ConversationResponse> createGroupChat(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.conversation.dto.CreateGroupChatRequest request) {
    return ResponseEntity.status(201)
        .body(conversationService.createGroupChat(user.getId(), request));
  }

  @Operation(summary = "Update group chat")
  @PatchMapping("/{id}")
  public ResponseEntity<ConversationResponse> updateGroupChat(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable java.util.UUID id,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.conversation.dto.UpdateGroupChatRequest request) {
    return ResponseEntity.ok(conversationService.updateGroupChat(user.getId(), id, request));
  }

  @Operation(summary = "Get conversation members")
  @GetMapping("/{id}/members")
  public ResponseEntity<
          List<com.example.chat_app_backend.chat.conversation.dto.ConversationMemberResponse>>
      getMembers(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    return ResponseEntity.ok(conversationService.getMembers(user.getId(), id));
  }

  @Operation(summary = "Add members to group")
  @PostMapping("/{id}/members")
  public ResponseEntity<Void> addMembers(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable java.util.UUID id,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.conversation.dto.AddMembersRequest request) {
    conversationService.addMembers(user.getId(), id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Remove member or leave")
  @DeleteMapping("/{id}/members/{userId}")
  public ResponseEntity<Void> removeMember(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable java.util.UUID id,
      @PathVariable java.util.UUID userId) {
    conversationService.removeMember(user.getId(), id, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Change member role")
  @PatchMapping("/{id}/members/{userId}/role")
  public ResponseEntity<Void> changeRole(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable java.util.UUID id,
      @PathVariable java.util.UUID userId,
      @Valid @RequestBody
          com.example.chat_app_backend.chat.conversation.dto.ChangeRoleRequest request) {
    conversationService.changeRole(user.getId(), id, userId, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Mute conversation")
  @PutMapping("/{id}/mute")
  public ResponseEntity<Void> muteConversation(
      @AuthenticationPrincipal UserDetailsImpl user,
      @PathVariable java.util.UUID id,
      @Valid @RequestBody com.example.chat_app_backend.chat.conversation.dto.MuteRequest request) {
    conversationService.muteConversation(user.getId(), id, request.mutedUntil());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Unmute conversation")
  @DeleteMapping("/{id}/mute")
  public ResponseEntity<Void> unmuteConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    conversationService.muteConversation(user.getId(), id, null);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Pin conversation")
  @PutMapping("/{id}/pin")
  public ResponseEntity<Void> pinConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    conversationService.pinConversation(user.getId(), id, true);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Unpin conversation")
  @DeleteMapping("/{id}/pin")
  public ResponseEntity<Void> unpinConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    conversationService.pinConversation(user.getId(), id, false);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Archive conversation")
  @PutMapping("/{id}/archive")
  public ResponseEntity<Void> archiveConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    conversationService.archiveConversation(user.getId(), id, true);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Unarchive conversation")
  @DeleteMapping("/{id}/archive")
  public ResponseEntity<Void> unarchiveConversation(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable java.util.UUID id) {
    conversationService.archiveConversation(user.getId(), id, false);
    return ResponseEntity.ok().build();
  }
}
