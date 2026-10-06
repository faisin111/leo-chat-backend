package com.example.chat_app_backend.service;

import com.example.chat_app_backend.payload.request.MediaConfirmRequest;
import com.example.chat_app_backend.payload.request.MediaPresignRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class MediaService {

  public ResponseEntity<Map<String, Object>> presign(UUID userId, MediaPresignRequest request) {
    // Scaffold response
    String storageKey = UUID.randomUUID().toString() + "-" + userId.toString() + ".tmp";
    String uploadUrl = "https://mock-storage.com/upload/" + storageKey;

    return ResponseEntity.ok(
        Map.of(
            "storageKey", storageKey,
            "uploadUrl", uploadUrl));
  }

  public ResponseEntity<Map<String, Object>> confirm(UUID userId, MediaConfirmRequest request) {
    // Scaffold response
    String finalUrl = "https://mock-storage.com/media/" + request.storageKey();
    UUID attachmentId = UUID.randomUUID();

    return ResponseEntity.ok(
        Map.of(
            "attachmentId", attachmentId,
            "url", finalUrl));
  }
}
