package com.example.chat_app_backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.UserBlockId.class)
public class UserBlock {

    @Id
    @Column(nullable = false)
    private UUID blockerId;

    @Id
    @Column(nullable = false)
    private UUID blockedId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getBlockerId() { return blockerId; }
    public void setBlockerId(UUID blockerId) { this.blockerId = blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public void setBlockedId(UUID blockedId) { this.blockedId = blockedId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class UserBlockId implements Serializable {
        private UUID blockerId;
        private UUID blockedId;
        public UserBlockId() {}
        public UserBlockId(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserBlockId that = (UserBlockId) o;
            return Objects.equals(blockerId, that.blockerId) && Objects.equals(blockedId, that.blockedId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
