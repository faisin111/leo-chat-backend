package com.example.chat_app_backend.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String username, String displayName, String role) {}
