package com.example.chat_app_backend.chat.conversation;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "conversation_members")
public class ConversationMember {
    @EmbeddedId
    private ConversationMemberId id;

    @Column(length = 10, nullable = false)
    private String role = "MEMBER";

    @Column(nullable = false)
    private long lastReadSeq = 0;

    private Instant mutedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    private Instant leftAt;
    
    private Instant pinnedAt;
    private Instant archivedAt;

    public ConversationMemberId getId() { return id; }
    public void setId(ConversationMemberId id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public long getLastReadSeq() { return lastReadSeq; }
    public void setLastReadSeq(long lastReadSeq) { this.lastReadSeq = lastReadSeq; }
    public Instant getMutedUntil() { return mutedUntil; }
    public void setMutedUntil(Instant mutedUntil) { this.mutedUntil = mutedUntil; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
    public Instant getLeftAt() { return leftAt; }
    public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }
    public Instant getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(Instant pinnedAt) { this.pinnedAt = pinnedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
