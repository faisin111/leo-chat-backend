package com.example.chat_app_backend.chat.ai.dto;

/** Shared limits and allowed values for AI requests. */
public final class AiOptions {
  public static final String MODEL_PATTERN = "leo-ultra|leo-reasoner|leo-coder|leo-fast";
  public static final int MAX_ATTACHMENTS = 5;
  public static final int MAX_ATTACHMENT_CHARS = 100_000;
  public static final int MAX_TOTAL_ATTACHMENT_CHARS = 200_000;

  private AiOptions() {}
}
