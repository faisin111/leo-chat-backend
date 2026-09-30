package com.example.chat_app_backend.payload.request;
import java.util.UUID;
public record TransferOwnershipRequest(UUID targetUserId, String password) {}
