package com.example.chat_app_backend.media;

import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.MessageAttachment;
import com.example.chat_app_backend.chat.message.MessageAttachmentRepository;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.common.exception.NotFoundException;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.media.dto.MediaConfirmRequest;
import com.example.chat_app_backend.media.dto.MediaPresignRequest;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Endpoints for managing media uploads")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "cookieAuth")
@StandardErrors
public class MediaController {

  @Autowired private MediaService mediaService;
  @Autowired private MessageAttachmentRepository attachmentRepository;
  @Autowired private MessageRepository messageRepository;
  @Autowired private ConversationService conversationService;

  @Operation(summary = "Get pre-signed upload URL")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/presign")
  public ResponseEntity<Map<String, Object>> presign(
      @AuthenticationPrincipal UserDetailsImpl userDetails,
      @Valid @RequestBody MediaPresignRequest request) {
    return ResponseEntity.ok(mediaService.presign(userDetails.getId(), request));
  }

  @Operation(summary = "Confirm upload")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/confirm")
  public ResponseEntity<Map<String, Object>> confirm(
      @AuthenticationPrincipal UserDetailsImpl userDetails,
      @Valid @RequestBody MediaConfirmRequest request) {
    return ResponseEntity.ok(mediaService.confirm(userDetails.getId(), request));
  }

  @Operation(summary = "Get an authorized signed download URL for a message attachment")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/download")
  public ResponseEntity<Map<String, Object>> download(
      @AuthenticationPrincipal UserDetailsImpl userDetails, @RequestParam String storageKey) {
    MessageAttachment attachment =
        attachmentRepository
            .findFirstByStorageKey(storageKey)
            .orElseThrow(() -> new NotFoundException("Attachment not found"));
    var message =
        messageRepository
            .findById(attachment.getMessageId())
            .orElseThrow(() -> new NotFoundException("Message not found"));
    conversationService.assertActiveMember(userDetails.getId(), message.getConversationId());
    if (message.getDeletedAt() != null) throw new NotFoundException("Attachment not found");
    return ResponseEntity.ok(mediaService.downloadUrl(storageKey));
  }
}
