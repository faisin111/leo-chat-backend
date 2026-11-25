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
@Table(name = "push_device_tokens")
public class PushDeviceToken {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true, length = 4096)
  private String token;

  @Column(nullable = false, length = 20)
  private String platform = "ANDROID";

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected PushDeviceToken() {}

  public PushDeviceToken(UUID userId, String token) {
    this.userId = userId;
    this.token = token;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getToken() {
    return token;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }
}
