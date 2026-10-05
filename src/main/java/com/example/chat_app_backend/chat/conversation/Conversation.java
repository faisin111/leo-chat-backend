package com.example.chat_app_backend.chat.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "conversations")
public class Conversation {
  @Id private UUID id = UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  @Column(length = 10, nullable = false)
  private ConversationType type;

  @Column(length = 100)
  private String title;

  @Column(length = 500)
  private String avatarUrl;

  @Column(length = 80, unique = true)
  private String directKey;

  @Column(nullable = false)
  private UUID createdBy;

  @Column(nullable = false)
  private long lastSeq = 0;

  private Instant lastMessageAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(length = 10, nullable = false)
  private String status = "ACTIVE"; // ACTIVE or LOCKED

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public ConversationType getType() {
    return type;
  }

  public void setType(ConversationType type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getDirectKey() {
    return directKey;
  }

  public void setDirectKey(String directKey) {
    this.directKey = directKey;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public long getLastSeq() {
    return lastSeq;
  }

  public void setLastSeq(long lastSeq) {
    this.lastSeq = lastSeq;
  }

  public Instant getLastMessageAt() {
    return lastMessageAt;
  }

  public void setLastMessageAt(Instant lastMessageAt) {
    this.lastMessageAt = lastMessageAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
