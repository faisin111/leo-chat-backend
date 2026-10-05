package com.example.chat_app_backend.chat.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "message_reactions")
@IdClass(MessageReaction.MessageReactionId.class)
public class MessageReaction {

  @Id
  @Column(nullable = false)
  private UUID messageId;

  @Id
  @Column(nullable = false)
  private UUID userId;

  @Id
  @Column(nullable = false, length = 16)
  private String emoji;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public UUID getMessageId() {
    return messageId;
  }

  public void setMessageId(UUID messageId) {
    this.messageId = messageId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getEmoji() {
    return emoji;
  }

  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public static class MessageReactionId implements Serializable {
    private UUID messageId;
    private UUID userId;
    private String emoji;

    public MessageReactionId() {}

    public MessageReactionId(UUID messageId, UUID userId, String emoji) {
      this.messageId = messageId;
      this.userId = userId;
      this.emoji = emoji;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MessageReactionId that = (MessageReactionId) o;
      return Objects.equals(messageId, that.messageId)
          && Objects.equals(userId, that.userId)
          && Objects.equals(emoji, that.emoji);
    }

    @Override
    public int hashCode() {
      return Objects.hash(messageId, userId, emoji);
    }
  }
}
