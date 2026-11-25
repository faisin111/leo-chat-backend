package com.example.chat_app_backend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenRequest(
    @NotBlank @Size(max = 4096) String token, @Size(max = 20) String platform) {}
