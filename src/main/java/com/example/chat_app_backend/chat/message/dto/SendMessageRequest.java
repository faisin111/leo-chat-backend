package com.example.chat_app_backend.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
    @NotNull @Schema(description = "Conversation ID") UUID conversationId,
    @NotBlank
        @Size(max = 64)
        @Schema(
            description = "Client-generated retry key, at most 64 characters",
            example = "msg-1")
        String clientMessageId,
    @Schema(
            description = "Message type: TEXT, IMAGE, or FILE; SYSTEM is server-only",
            example = "TEXT")
        String type,
    @Size(max = 4000)
        @Schema(description = "Message content, at most 4000 characters", example = "Hello")
        String content,
    @Schema(description = "Optional message ID in this conversation") UUID replyToId,
    @Size(max = 300) @Schema(description = "Confirmed uploaded media storage key")
        String storageKey,
    @Size(max = 200) String fileName,
    @Size(max = 100) String mimeType,
    @jakarta.validation.constraints.Positive Long sizeBytes) {

  public SendMessageRequest(
      UUID conversationId, String clientMessageId, String type, String content, UUID replyToId) {
    this(conversationId, clientMessageId, type, content, replyToId, null, null, null, null);
  }

  @AssertTrue(message = "Content is required for text or a storageKey is required for media")
  public boolean isContentOrAttachmentPresent() {
    boolean media = type != null && !"TEXT".equalsIgnoreCase(type);
    return media
        ? storageKey != null && !storageKey.isBlank()
        : content != null && !content.isBlank();
  }
}
