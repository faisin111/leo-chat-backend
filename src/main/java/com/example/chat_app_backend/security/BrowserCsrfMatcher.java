package com.example.chat_app_backend.security;

import com.example.chat_app_backend.security.jwt.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Browser cookie requests need CSRF; explicit bearer-only API requests do not. */
public class BrowserCsrfMatcher implements RequestMatcher {
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
  private final AuthCookies cookies;

  public BrowserCsrfMatcher(AuthCookies cookies) {
    this.cookies = cookies;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    if (SAFE_METHODS.contains(request.getMethod())) return false;
    String path = request.getServletPath();
    // SockJS transport is protected by the explicit origin allowlist and STOMP authentication.
    if (path.equals("/ws") || path.startsWith("/ws/")) return false;
    String header = request.getHeader("Authorization");
    boolean bearerOnly =
        header != null
            && header.regionMatches(true, 0, "Bearer ", 0, 7)
            && cookies.accessToken(request) == null
            && cookies.refreshToken(request) == null;
    return !bearerOnly;
  }
}
