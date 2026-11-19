package com.example.chat_app_backend.security.jwt;

import com.example.chat_app_backend.security.services.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthTokenFilter extends OncePerRequestFilter {
  private static final String BEARER_PREFIX = "Bearer ";
  // Reachable while a password change is pending; everything else returns 403.
  private static final java.util.Set<String> PASSWORD_CHANGE_ALLOWED =
      java.util.Set.of(
          "/api/v1/auth/change-password",
          "/api/v1/auth/logout",
          "/api/v1/auth/logout-all",
          "/api/v1/auth/refresh",
          "/api/v1/auth/csrf",
          "/api/v1/users/me");
  private final AuthCookies cookies;
  private final JwtUtils jwtUtils;
  private final UserDetailsServiceImpl users;

  public AuthTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl users, AuthCookies cookies) {
    this.cookies = cookies;
    this.jwtUtils = jwtUtils;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    String cookieToken = cookies.accessToken(request);
    String bearerToken = extractBearerToken(header);
    // Empty or absent headers may fall back to the browser cookie. An explicit bearer credential
    // must stay authoritative; never authenticate a different cookie identity behind it.
    boolean explicitBearer = bearerToken != null && !bearerToken.isBlank();
    String token = explicitBearer ? bearerToken : cookieToken;
    if (token != null) {
      if (jwtUtils.validateJwtToken(token)) {
        try {
          UserDetails user = users.loadUserByUsername(jwtUtils.getUserNameFromJwtToken(token));
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
          if (user instanceof com.example.chat_app_backend.security.services.UserDetailsImpl details
              && details.isMustChangePassword()
              && request.getRequestURI().startsWith("/api/")
              && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response
                .getWriter()
                .write(
                    "{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                        + "\"message\":\"Change your password to continue\"}");
            return;
          }
        } catch (AuthenticationException | JwtException | IllegalArgumentException exception) {
          // Reject invalid credentials without logging bearer tokens or user details.
          SecurityContextHolder.clearContext();
        }
      }
    }
    chain.doFilter(request, response);
  }

  private String extractBearerToken(String header) {
    if (header == null
        || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length()).trim();
  }
}
