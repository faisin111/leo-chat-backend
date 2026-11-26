package com.example.chat_app_backend.user;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Tracks authenticated STOMP sessions for real-time, per-instance presence. */
@Service
public class UserPresenceService {
  private final UserRepository userRepository;
  private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
  private final Map<String, UUID> sessionUsers = new ConcurrentHashMap<>();

  public UserPresenceService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    if (accessor.getUser() == null || accessor.getSessionId() == null) return;
    userRepository
        .findByUsername(accessor.getUser().getName())
        .ifPresent(
            user -> {
              String sessionId = accessor.getSessionId();
              sessionUsers.put(sessionId, user.getId());
              userSessions
                  .computeIfAbsent(user.getId(), ignored -> ConcurrentHashMap.newKeySet())
                  .add(sessionId);
            });
  }

  @EventListener
  @Transactional
  public void onDisconnected(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();
    if (sessionId == null) return;
    UUID userId = sessionUsers.remove(sessionId);
    if (userId == null) return;
    Set<String> sessions = userSessions.get(userId);
    if (sessions == null) return;
    sessions.remove(sessionId);
    if (!sessions.isEmpty()) return;
    userSessions.remove(userId, sessions);
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              user.setLastSeenAt(Instant.now());
              userRepository.save(user);
            });
  }

  public boolean isOnline(UUID userId) {
    Set<String> sessions = userSessions.get(userId);
    return sessions != null && !sessions.isEmpty();
  }
}
