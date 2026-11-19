package com.example.chat_app_backend.chat.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Ask Leo AI. {@code prompt} may be blank only when text attachments are sent. {@code model},
 * {@code reasoning}, {@code webSearch} and {@code attachments} are optional.
 */
public record AiChatRequest(
    @NotNull UUID conversationId,
    @Size(max = 4000) String prompt,
    @Size(max = 64) String clientMessageId,
    @Pattern(regexp = AiOptions.MODEL_PATTERN) String model,
    Boolean reasoning,
    Boolean webSearch,
    @Size(max = AiOptions.MAX_ATTACHMENTS) List<@Valid AiAttachmentText> attachments) {

  /** Backwards-compatible constructor for clients that only send prompt fields. */
  public AiChatRequest(UUID conversationId, String prompt, String clientMessageId) {
    this(conversationId, prompt, clientMessageId, null, null, null, null);
  }
}
