package com.example.chat_app_backend.chat.ai;

import com.example.chat_app_backend.common.exception.RateLimitException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window limit on AI generations, so one account cannot run up Gemini costs.
 * In-memory: each backend instance counts separately.
 */
@Component
public class AiRateLimiter {

  private static final long WINDOW_MS = 60_000;

  private final int maxPerMinute;
  private final Clock clock;
  private final Map<UUID, Deque<Long>> calls = new ConcurrentHashMap<>();

  @Autowired
  public AiRateLimiter(@Value("${app.ai.max-requests-per-minute:20}") int maxPerMinute) {
    this(maxPerMinute, Clock.systemUTC());
  }

  AiRateLimiter(int maxPerMinute, Clock clock) {
    this.maxPerMinute = maxPerMinute;
    this.clock = clock;
  }

  public void acquire(UUID userId) {
    if (maxPerMinute <= 0) return;
    long now = clock.millis();
    Deque<Long> window = calls.computeIfAbsent(userId, id -> new ArrayDeque<>());
    synchronized (window) {
      while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) window.pollFirst();
      if (window.size() >= maxPerMinute) {
        throw new RateLimitException("Too many AI requests; try again in a minute");
      }
      window.addLast(now);
    }
  }
}
