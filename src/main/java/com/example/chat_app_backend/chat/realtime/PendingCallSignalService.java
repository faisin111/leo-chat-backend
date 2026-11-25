package com.example.chat_app_backend.chat.realtime;

import com.example.chat_app_backend.chat.realtime.dto.CallSignalEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * Buffers WebRTC OFFER and ICE_CANDIDATE signals briefly while an offline callee wakes up from an
 * FCM incoming-call push and connects to STOMP.
 */
@Service
public class PendingCallSignalService {

  private static final Duration CALL_TTL = Duration.ofSeconds(60);
  private static final int MAX_BUFFERED_CANDIDATES = 64;

  private final Map<String, Map<UUID, PendingCall>> pendingByTarget = new ConcurrentHashMap<>();

  public void recordInvite(String targetUsername, CallSignalEvent inviteEvent) {
    pruneExpired(targetUsername);
    Map<UUID, PendingCall> userCalls =
        pendingByTarget.computeIfAbsent(targetUsername, k -> new ConcurrentHashMap<>());
    PendingCall pending =
        userCalls.computeIfAbsent(
            inviteEvent.callId(),
            k ->
                new PendingCall(
                    inviteEvent.conversationId(),
                    inviteEvent.callId(),
                    inviteEvent.fromUserId(),
                    inviteEvent.fromUsername(),
                    inviteEvent.targetUserId()));
    if (inviteEvent.sdp() != null && !inviteEvent.sdp().isBlank()) {
      pending.offerSdp = inviteEvent.sdp();
    }
    pending.expiresAt = Instant.now().plus(CALL_TTL);
  }

  /**
   * Buffers OFFER or ICE_CANDIDATE if an active FCM-woken call exists for this callee/callId.
   *
   * @return true if the signal belongs to a pending push call (caller should stay ringing).
   */
  public boolean bufferIfPending(String targetUsername, CallSignalEvent event) {
    pruneExpired(targetUsername);
    Map<UUID, PendingCall> userCalls = pendingByTarget.get(targetUsername);
    if (userCalls == null) return false;
    PendingCall pending = userCalls.get(event.callId());
    if (pending == null || pending.isExpired()) {
      userCalls.remove(event.callId());
      return false;
    }

    if ("OFFER".equals(event.action()) && event.sdp() != null && !event.sdp().isBlank()) {
      pending.offerSdp = event.sdp();
    } else if ("ICE_CANDIDATE".equals(event.action())
        && pending.iceCandidates.size() < MAX_BUFFERED_CANDIDATES) {
      pending.iceCandidates.add(event);
    }
    pending.expiresAt = Instant.now().plus(CALL_TTL);
    return true;
  }

  /** Removes and returns buffered OFFER + ICE_CANDIDATE events when the callee connects. */
  public List<CallSignalEvent> drainSignals(String targetUsername) {
    pruneExpired(targetUsername);
    Map<UUID, PendingCall> userCalls = pendingByTarget.remove(targetUsername);
    if (userCalls == null || userCalls.isEmpty()) {
      return List.of();
    }

    List<CallSignalEvent> events = new ArrayList<>();
    for (PendingCall pending : userCalls.values()) {
      if (pending.isExpired()) continue;
      if (pending.offerSdp != null) {
        events.add(
            new CallSignalEvent(
                pending.conversationId,
                pending.callId,
                pending.fromUserId,
                pending.fromUsername,
                pending.targetUserId,
                "OFFER",
                pending.offerSdp,
                null,
                null,
                null));
      }
      events.addAll(pending.iceCandidates);
    }
    return events;
  }

  public void clearCall(UUID callId) {
    if (callId == null) return;
    pendingByTarget
        .values()
        .forEach(
            calls -> {
              calls.remove(callId);
            });
  }

  private void pruneExpired(String targetUsername) {
    Map<UUID, PendingCall> userCalls = pendingByTarget.get(targetUsername);
    if (userCalls == null) return;
    userCalls.entrySet().removeIf(entry -> entry.getValue().isExpired());
    if (userCalls.isEmpty()) {
      pendingByTarget.remove(targetUsername, userCalls);
    }
  }

  private static final class PendingCall {
    final UUID conversationId;
    final UUID callId;
    final UUID fromUserId;
    final String fromUsername;
    final UUID targetUserId;
    final List<CallSignalEvent> iceCandidates = new CopyOnWriteArrayList<>();
    volatile String offerSdp;
    volatile Instant expiresAt = Instant.now().plus(CALL_TTL);

    PendingCall(
        UUID conversationId, UUID callId, UUID fromUserId, String fromUsername, UUID targetUserId) {
      this.conversationId = conversationId;
      this.callId = callId;
      this.fromUserId = fromUserId;
      this.fromUsername = fromUsername;
      this.targetUserId = targetUserId;
    }

    boolean isExpired() {
      return Instant.now().isAfter(expiresAt);
    }
  }
}
