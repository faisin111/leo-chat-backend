package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.chat.ai.AiService;
import com.example.chat_app_backend.chat.ai.dto.AiChatRequest;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "Gemini-powered chat assistant")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "cookieAuth")
@StandardErrors
public class AiController {

  private final AiService aiService;

  public AiController(AiService aiService) {
    this.aiService = aiService;
  }

  @Operation(summary = "Ask Leo AI inside a conversation")
  @ApiResponse(responseCode = "200", description = "Persisted AI message")
  @PostMapping("/chat")
  public ResponseEntity<MessageResponse> chat(
      @AuthenticationPrincipal UserDetailsImpl user, @Valid @RequestBody AiChatRequest request) {
    return ResponseEntity.ok(aiService.chat(user.getId(), request));
  }

  @Operation(summary = "Get or create the user's single personal AI conversation")
  @PostMapping("/conversation")
  public ResponseEntity<com.example.chat_app_backend.chat.conversation.dto.ConversationResponse>
      getOrCreateConversation(@AuthenticationPrincipal UserDetailsImpl user) {
    return ResponseEntity.ok(aiService.getOrCreateConversation(user.getId()));
  }

  @Operation(summary = "Permanently delete the user's personal AI conversation")
  @org.springframework.web.bind.annotation.DeleteMapping("/conversation")
  public ResponseEntity<Void> deleteConversation(@AuthenticationPrincipal UserDetailsImpl user) {
    aiService.deleteConversation(user.getId());
    return ResponseEntity.noContent().build();
  }
}
