package com.example.chat_app_backend.notification;

import com.example.chat_app_backend.common.dto.ApiMessage;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class AppNotificationController {
  private final AppNotificationService service;

  public AppNotificationController(AppNotificationService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<Page<AppNotificationResponse>> list(
      @AuthenticationPrincipal UserDetailsImpl user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return ResponseEntity.ok(service.list(user.getId(), page, size, unreadOnly));
  }

  @GetMapping("/unread-count")
  public ResponseEntity<Map<String, Long>> unreadCount(
      @AuthenticationPrincipal UserDetailsImpl user) {
    return ResponseEntity.ok(Map.of("unreadCount", service.unreadCount(user.getId())));
  }

  @PatchMapping("/{id}/read")
  public ResponseEntity<ApiMessage> markRead(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
    service.markRead(user.getId(), id);
    return ResponseEntity.ok(new ApiMessage("Notification marked as read"));
  }

  @PatchMapping("/read-all")
  public ResponseEntity<Map<String, Integer>> markAllRead(
      @AuthenticationPrincipal UserDetailsImpl user) {
    return ResponseEntity.ok(Map.of("markedRead", service.markAllRead(user.getId())));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
    service.delete(user.getId(), id);
    return ResponseEntity.noContent().build();
  }
}
