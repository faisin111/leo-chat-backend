package com.example.chat_app_backend.exception;

public class AiTimeoutException extends RuntimeException {
  public AiTimeoutException(String message) {
    super(message);
  }
}
