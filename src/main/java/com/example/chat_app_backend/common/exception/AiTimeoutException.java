package com.example.chat_app_backend.common.exception;

public class AiTimeoutException extends RuntimeException {
  public AiTimeoutException(String message) {
    super(message);
  }
}
