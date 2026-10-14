package com.example.chat_app_backend.common.enums;

/** Persisted user lifecycle values. Database columns remain strings for compatibility. */
public enum UserStatus {
  ACTIVE,
  DISABLED,
  BANNED,
  DELETED
}
