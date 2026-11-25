package com.example.chat_app_backend.notification;

import com.example.chat_app_backend.common.dto.ApiMessage;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/push-token")
public class PushTokenController {
  private final PushNotificationService pushNotificationService;

  public PushTokenController(PushNotificationService pushNotificationService) {
    this.pushNotificationService = pushNotificationService;
  }

  @PostMapping
  public ResponseEntity<ApiMessage> register(
      @AuthenticationPrincipal UserDetailsImpl user, @Valid @RequestBody PushTokenRequest request) {
    pushNotificationService.register(user.getId(), request);
    return ResponseEntity.ok(new ApiMessage("Push token registered"));
  }

  @DeleteMapping
  public ResponseEntity<Void> unregister(
      @AuthenticationPrincipal UserDetailsImpl user, @Valid @RequestBody PushTokenRequest request) {
    pushNotificationService.unregister(user.getId(), request.token());
    return ResponseEntity.noContent().build();
  }
}
