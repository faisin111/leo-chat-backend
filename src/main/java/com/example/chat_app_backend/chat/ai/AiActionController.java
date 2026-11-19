package com.example.chat_app_backend.chat.ai;

import com.example.chat_app_backend.chat.ai.dto.AiActionRequest;
import com.example.chat_app_backend.chat.ai.dto.AiActionResponse;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/ai/actions")
@Tag(name = "AI Actions", description = "Safe AI access to project capabilities")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "cookieAuth")
@StandardErrors
public class AiActionController {

  private final AiActionService aiActionService;

  public AiActionController(AiActionService aiActionService) {
    this.aiActionService = aiActionService;
  }

  @Operation(
      summary = "Execute an AI action",
      description =
          "Read actions execute directly. SEND_MESSAGE and START_CALL return confirmationRequired=true until confirmed=true is sent.")
  @PostMapping
  public ResponseEntity<AiActionResponse> execute(
      @AuthenticationPrincipal UserDetailsImpl user, @Valid @RequestBody AiActionRequest request) {
    return ResponseEntity.ok(aiActionService.execute(user.getId(), request));
  }
}
