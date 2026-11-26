package com.example.chat_app_backend.chat.realtime;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/** Tracks short-lived call state so an unanswered call can become a missed-call notification. */
@Service
public class ActiveCallService {
  private final ConcurrentMap<UUID, CallState> calls = new ConcurrentHashMap<>();

  public void start(CallState call) {
    calls.putIfAbsent(call.callId(), call);
  }

  public void markAnswered(UUID callId) {
    calls.computeIfPresent(callId, (ignored, call) -> call.withAnswered(true));
  }

  public CallState takeMissed(UUID callId) {
    CallState call = calls.remove(callId);
    return call != null && !call.answered() ? call : null;
  }

  public void finish(UUID callId) {
    if (callId != null) calls.remove(callId);
  }

  public record CallState(
      UUID callId,
      UUID conversationId,
      UUID callerId,
      UUID calleeId,
      String callerName,
      boolean video,
      boolean answered) {
    public CallState withAnswered(boolean value) {
      return new CallState(callId, conversationId, callerId, calleeId, callerName, video, value);
    }
  }
}
