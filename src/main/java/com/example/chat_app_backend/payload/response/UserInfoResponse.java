package com.example.chat_app_backend.payload.response;
import java.util.UUID;
public record UserInfoResponse(UUID id, String username, String email) {}
