package com.example.chat_app_backend.chat.message.dto;

import java.util.UUID;

public record MessageAttachmentResponse(
    UUID id, String storageKey, String fileName, String mimeType, Long sizeBytes) {}
