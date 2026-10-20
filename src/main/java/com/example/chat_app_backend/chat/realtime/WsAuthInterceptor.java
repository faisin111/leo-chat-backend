package com.example.chat_app_backend.chat.realtime;

import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.security.services.UserDetailsServiceImpl;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class WsAuthInterceptor implements ChannelInterceptor {

  private static final Pattern CONVERSATION_TOPIC =
      Pattern.compile("^/topic/conversations\\.([0-9a-fA-F-]{36})(?:\\.typing)?$");
  private static final Set<String> SEND_DESTINATIONS =
      Set.of("/app/chat.send", "/app/chat.typing", "/app/chat.read", "/app/call.signal");
  private static final Set<String> USER_QUEUES =
      Set.of(
          "/user/queue/acks",
          "/user/queue/errors",
          "/user/queue/notifications",
          "/user/queue/calls");
  private final ConversationService conversationService;
  private final JwtUtils jwtUtils;
  private final UserDetailsServiceImpl userDetailsService;

  public WsAuthInterceptor(
      JwtUtils jwtUtils,
      UserDetailsServiceImpl userDetailsService,
      ConversationService conversationService) {
    this.conversationService = conversationService;
    this.jwtUtils = jwtUtils;
    this.userDetailsService = userDetailsService;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) return message;
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      // Ideally extract from Cookie if possible, but STOMP over WebSocket
      // usually passes tokens in headers (e.g., native apps) or via query params.
      // For now, check standard Authorization header if provided by client.
      String authHeader = accessor.getFirstNativeHeader("Authorization");
      if (authHeader == null) {
        authHeader = accessor.getFirstNativeHeader("token");
      }
      if (authHeader == null) {
        authHeader = accessor.getFirstNativeHeader("access_token");
      }
      if (authHeader == null
          && accessor.getUser() instanceof Authentication authentication
          && authentication.isAuthenticated()
          && authentication.getPrincipal() instanceof UserDetailsImpl) {
        return message;
      }
      if (authHeader == null || authHeader.isBlank()) throw new MessagingException("Unauthorized");
      String token =
          authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
      if (!jwtUtils.validateJwtToken(token)) throw new MessagingException("Unauthorized");
      String username = jwtUtils.getUserNameFromJwtToken(token);
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);
      if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked())
        throw new MessagingException("Unauthorized");
      accessor.setUser(
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
        || StompCommand.SEND.equals(accessor.getCommand())) {
      if (!(accessor.getUser() instanceof Authentication auth)
          || !auth.isAuthenticated()
          || !(auth.getPrincipal() instanceof UserDetailsImpl user)) {
        throw new MessagingException("Unauthorized");
      }
      String destination = accessor.getDestination();
      if (StompCommand.SEND.equals(accessor.getCommand())) {
        if (!SEND_DESTINATIONS.contains(destination == null ? "" : destination)) {
          throw new MessagingException("Forbidden destination");
        }
      } else if (!USER_QUEUES.contains(destination == null ? "" : destination)) {
        Matcher matcher = CONVERSATION_TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) throw new MessagingException("Forbidden destination");
        conversationService.assertActiveMember(user.getId(), UUID.fromString(matcher.group(1)));
      }
    }
    return message;
  }
}
