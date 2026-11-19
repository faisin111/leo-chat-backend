package com.example.chat_app_backend.common.dto;

public class ApiMessage {
  private String message;

  public ApiMessage(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
