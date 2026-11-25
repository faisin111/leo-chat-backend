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
import java.time.Instant;
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
  private final AppNotificationService appNotificationService;
  private final boolean enabled;

  public PushNotificationService(
      PushDeviceTokenRepository tokenRepository,
      AppNotificationService appNotificationService,
      @Value("${firebase.project-id:}") String projectId,
      @Value("${firebase.private-key-id:}") String privateKeyId,
      @Value("${firebase.client-id:}") String clientId,
      @Value("${firebase.client-email:}") String clientEmail,
      @Value("${firebase.private-key:}") String privateKey) {
    this.tokenRepository = tokenRepository;
    this.appNotificationService = appNotificationService;
    this.enabled = initialize(projectId, privateKeyId, clientId, clientEmail, privateKey);
  }

  private boolean initialize(
      String projectId,
      String privateKeyId,
      String clientId,
      String clientEmail,
      String privateKey) {
    if (projectId.isBlank()
        || privateKeyId.isBlank()
        || clientId.isBlank()
        || clientEmail.isBlank()
        || privateKey.isBlank()) {
      log.info("Firebase push notifications disabled because credentials are not configured");
      return false;
    }
    try {
      String json =
          String.format(
              "{\"type\":\"service_account\",\"project_id\":\"%s\",\"private_key_id\":\"%s\",\"private_key\":\"%s\",\"client_email\":\"%s\",\"client_id\":\"%s\"}",
              escape(projectId),
              escape(privateKeyId),
              escape(privateKey.replace("\\n", "\n")),
              escape(clientEmail),
              escape(clientId));
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

  public boolean sendIncomingCall(
      UUID targetUserId, UUID callId, UUID conversationId, UUID callerId, String callerName) {
    return sendIncomingCall(targetUserId, callId, conversationId, callerId, callerName, false);
  }

  public boolean sendIncomingCall(
      UUID targetUserId,
      UUID callId,
      UUID conversationId,
      UUID callerId,
      String callerName,
      boolean isVideo) {
    appNotificationService.create(
        targetUserId,
        "INCOMING_CALL",
        isVideo ? "Incoming video call" : "Incoming call",
        callerName + " is calling you",
        callId,
        conversationId);
    if (!enabled) return false;
    boolean deliveredAny = false;
    for (PushDeviceToken device : tokenRepository.findAllByUserId(targetUserId)) {
      try {
        Message message =
            Message.builder()
                .setToken(device.getToken())
                .putAllData(
                    Map.ofEntries(
                        Map.entry("type", "INCOMING_CALL"),
                        Map.entry("category", "CALL"),
                        Map.entry("callType", isVideo ? "video" : "audio"),
                        Map.entry("notificationId", callId.toString()),
                        Map.entry("callId", callId.toString()),
                        Map.entry("conversationId", conversationId.toString()),
                        Map.entry("callerId", callerId.toString()),
                        Map.entry("callerName", callerName),
                        Map.entry("timestamp", Instant.now().toString()),
                        Map.entry("groupKey", "calls"),
                        Map.entry("clickAction", "OPEN_CALL")))
                .setAndroidConfig(
                    AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build();
        FirebaseMessaging.getInstance().send(message);
        deliveredAny = true;
      } catch (Exception ex) {
        log.warn("Unable to send incoming call push to user {}", targetUserId, ex);
      }
    }
    return deliveredAny;
  }

  public void sendMessageNotification(
      UUID targetUserId,
      UUID messageId,
      UUID conversationId,
      UUID senderId,
      String senderName,
      String messageType,
      String preview) {
    appNotificationService.create(
        targetUserId, "NEW_MESSAGE", senderName, preview, messageId, conversationId);
    send(
        targetUserId,
        "NEW_MESSAGE",
        senderName,
        preview,
        Map.ofEntries(
            Map.entry("type", "NEW_MESSAGE"),
            Map.entry("messageId", messageId.toString()),
            Map.entry("conversationId", conversationId.toString()),
            Map.entry("senderId", senderId.toString()),
            Map.entry("messageType", messageType),
            Map.entry("senderName", senderName),
            Map.entry("preview", preview),
            Map.entry("timestamp", Instant.now().toString()),
            Map.entry("category", "MESSAGE"),
            Map.entry("groupKey", "conversation-" + conversationId),
            Map.entry("notificationId", messageId.toString()),
            Map.entry("clickAction", "OPEN_CONVERSATION")));
  }

  public void sendReactionNotification(
      UUID targetUserId, UUID messageId, UUID conversationId, String senderName, String emoji) {
    appNotificationService.create(
        targetUserId,
        "MESSAGE_REACTION",
        senderName,
        "Reacted " + emoji + " to your message",
        messageId,
        conversationId);
    send(
        targetUserId,
        "MESSAGE_REACTION",
        senderName,
        "Reacted " + emoji + " to your message",
        Map.ofEntries(
            Map.entry("type", "MESSAGE_REACTION"),
            Map.entry("messageId", messageId.toString()),
            Map.entry("conversationId", conversationId.toString()),
            Map.entry("emoji", emoji),
            Map.entry("timestamp", Instant.now().toString()),
            Map.entry("category", "REACTION"),
            Map.entry("groupKey", "conversation-" + conversationId),
            Map.entry("notificationId", messageId + "-reaction-" + emoji),
            Map.entry("clickAction", "OPEN_CONVERSATION")));
  }

  private void send(
      UUID targetUserId, String type, String title, String body, Map<String, String> data) {
    if (!enabled) return;
    for (PushDeviceToken device : tokenRepository.findAllByUserId(targetUserId)) {
      try {
        Message message =
            Message.builder()
                .setToken(device.getToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .setAndroidConfig(
                    AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build();
        FirebaseMessaging.getInstance().send(message);
      } catch (Exception ex) {
        log.warn("Unable to send {} push to user {}", type, targetUserId, ex);
      }
    }
  }
}
