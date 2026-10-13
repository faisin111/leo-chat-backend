package com.example.chat_app_backend.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
public class AuthCookies {
  public static final String ACCESS_PATH = "/";
  public static final String REFRESH_PATH = "/api/v1/auth/refresh";
  private final AuthCookieProperties properties;

  public AuthCookies(AuthCookieProperties properties) {
    this.properties = properties;
  }

  public String accessToken(HttpServletRequest request) {
    return value(request, properties.accessName());
  }

  public String refreshToken(HttpServletRequest request) {
    return value(request, properties.refreshName());
  }

  private String value(HttpServletRequest request, String name) {
    Cookie cookie = WebUtils.getCookie(request, name);
    return cookie == null ? null : cookie.getValue();
  }

  private ResponseCookie cookie(String name, String value, String path, long seconds) {
    return ResponseCookie.from(name, value)
        .path(path)
        .httpOnly(true)
        .secure(properties.secure())
        .sameSite(properties.sameSite())
        .maxAge(seconds)
        .build();
  }

  public ResponseCookie access(String token, long seconds) {
    return cookie(properties.accessName(), token, ACCESS_PATH, seconds);
  }

  public ResponseCookie refresh(String token, long seconds) {
    return cookie(properties.refreshName(), token, REFRESH_PATH, seconds);
  }

  public ResponseCookie clearLegacyAccess() {
    return cookie(properties.accessName(), "", "/api", 0);
  }

  public ResponseCookie clearAccess() {
    return access("", 0);
  }

  public ResponseCookie clearRefresh() {
    return refresh("", 0);
  }
}
