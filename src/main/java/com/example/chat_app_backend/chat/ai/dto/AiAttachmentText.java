package com.example.chat_app_backend.chat.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A text file the client read locally. Sent to the model for this turn only, never stored. */
public record AiAttachmentText(
    @NotBlank @Size(max = 255) String name,
    @NotNull @Size(max = AiOptions.MAX_ATTACHMENT_CHARS) String text) {}
