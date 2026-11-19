package com.example.chat_app_backend.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String traceId,
    List<Field> errors) {
  public record Field(String field, String message) {}
}
