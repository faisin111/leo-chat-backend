package com.example.chat_app_backend.chat.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "messages",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"conversationId", "seq"}),
      @UniqueConstraint(columnNames = {"conversationId", "senderId", "clientMessageId"})
    })
public class Message {
  @Id private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID conversationId;

  @Column(nullable = false)
  private UUID senderId;

  @Column(nullable = false)
  private long seq;

  @Column(length = 64, nullable = false)
  private String clientMessageId;

  @Enumerated(EnumType.STRING)
  @Column(length = 15, nullable = false)
  private MessageType type = MessageType.TEXT;

  @Column(columnDefinition = "TEXT")
  private String content;

  private UUID replyToId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  private Instant editedAt;
  private Instant deletedAt;

  @Column(nullable = false)
  private boolean removedByAdmin = false;

  @Column(length = 300)
  private String removalReason;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public void setConversationId(UUID conversationId) {
    this.conversationId = conversationId;
  }

  public UUID getSenderId() {
    return senderId;
  }

  public void setSenderId(UUID senderId) {
    this.senderId = senderId;
  }

  public long getSeq() {
    return seq;
  }

  public void setSeq(long seq) {
    this.seq = seq;
  }

  public String getClientMessageId() {
    return clientMessageId;
  }

  public void setClientMessageId(String clientMessageId) {
    this.clientMessageId = clientMessageId;
  }

  public MessageType getType() {
    return type;
  }

  public void setType(MessageType type) {
    this.type = type;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public UUID getReplyToId() {
    return replyToId;
  }

  public void setReplyToId(UUID replyToId) {
    this.replyToId = replyToId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public void setEditedAt(Instant editedAt) {
    this.editedAt = editedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public boolean isRemovedByAdmin() {
    return removedByAdmin;
  }

  public void setRemovedByAdmin(boolean removedByAdmin) {
    this.removedByAdmin = removedByAdmin;
  }

  public String getRemovalReason() {
    return removalReason;
  }

  public void setRemovalReason(String removalReason) {
    this.removalReason = removalReason;
  }
}
