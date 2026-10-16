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
    // Token API clients do not need a CSRF token. They authenticate each request with a bearer
    // access token instead of relying on ambient browser cookies.
    String header = request.getHeader("Authorization");
    if (header != null
        && header.regionMatches(true, 0, "Bearer ", 0, 7)
        && !header.substring(7).trim().isBlank()) return false;

    // Login, registration, and token exchange are explicit API operations. They do not rely on
    // an existing authenticated cookie, so requiring a CSRF bootstrap request would block token
    // clients before they have a token. Cookie-authenticated protected writes remain protected.
    return !Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification")
        .contains(path);
  }
}
