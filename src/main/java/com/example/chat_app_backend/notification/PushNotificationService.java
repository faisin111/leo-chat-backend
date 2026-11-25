package com.example.chat_app_backend.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {
  private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
  private final PushDeviceTokenRepository tokenRepository;
  private final boolean enabled;

  public PushNotificationService(
      PushDeviceTokenRepository tokenRepository,
      @Value("${firebase.project-id:}") String projectId,
      @Value("${firebase.client-email:}") String clientEmail,
      @Value("${firebase.private-key:}") String privateKey) {
    this.tokenRepository = tokenRepository;
    this.enabled = initialize(projectId, clientEmail, privateKey);
  }

  private boolean initialize(String projectId, String clientEmail, String privateKey) {
    if (projectId.isBlank() || clientEmail.isBlank() || privateKey.isBlank()) {
      log.info("Firebase push notifications disabled because credentials are not configured");
      return false;
    }
    try {
      String json =
          String.format(
              "{\"type\":\"service_account\",\"project_id\":\"%s\",\"private_key\":\"%s\",\"client_email\":\"%s\"}",
              escape(projectId), escape(privateKey.replace("\\n", "\n")), escape(clientEmail));
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(
                  GoogleCredentials.fromStream(
                      new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
              .setProjectId(projectId)
              .build();
      if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options);
      return true;
    } catch (Exception ex) {
      log.error("Firebase push notifications could not be initialized", ex);
      return false;
    }
  }

  private static String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  public void register(UUID userId, PushTokenRequest request) {
    tokenRepository
        .findByToken(request.token())
        .ifPresentOrElse(
            existing -> {
              existing.setUserId(userId);
              existing.setPlatform(request.platform() == null ? "ANDROID" : request.platform());
              existing.touch();
              tokenRepository.save(existing);
            },
            () -> {
              PushDeviceToken token = new PushDeviceToken(userId, request.token());
              token.setPlatform(request.platform() == null ? "ANDROID" : request.platform());
              tokenRepository.save(token);
            });
  }

  public void unregister(UUID userId, String token) {
    tokenRepository.deleteByTokenAndUserId(token, userId);
  }

  public void sendIncomingCall(
      UUID targetUserId, UUID callId, UUID conversationId, UUID callerId, String callerName) {
    if (!enabled) return;
    for (PushDeviceToken device : tokenRepository.findAllByUserId(targetUserId)) {
      try {
        Message message =
            Message.builder()
                .setToken(device.getToken())
                .setNotification(
                    Notification.builder()
                        .setTitle("Incoming call")
                        .setBody(callerName + " is calling you")
                        .build())
                .putAllData(
                    Map.of(
                        "type",
                        "INCOMING_CALL",
                        "callId",
                        callId.toString(),
                        "conversationId",
                        conversationId.toString(),
                        "callerId",
                        callerId.toString(),
                        "callerName",
                        callerName))
                .setAndroidConfig(
                    AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build();
        FirebaseMessaging.getInstance().send(message);
      } catch (Exception ex) {
        log.warn("Unable to send incoming call push to user {}", targetUserId, ex);
      }
    }
  }
}
