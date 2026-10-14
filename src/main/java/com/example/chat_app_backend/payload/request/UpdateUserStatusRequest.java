package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED|BANNED") String status,
    @Size(max = 500) String reason) {}
