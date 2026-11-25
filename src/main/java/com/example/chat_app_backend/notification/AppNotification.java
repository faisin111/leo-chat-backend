package com.example.chat_app_backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_notifications")
public class AppNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String body;

  private UUID referenceId;
  private UUID conversationId;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant readAt;

  protected AppNotification() {}

  public AppNotification(
      UUID userId, String type, String title, String body, UUID referenceId, UUID conversationId) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.referenceId = referenceId;
    this.conversationId = conversationId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void markRead() {
    this.readAt = Instant.now();
  }
}
