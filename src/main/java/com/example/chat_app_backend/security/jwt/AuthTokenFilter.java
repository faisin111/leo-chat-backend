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
    // Browser clients can send a stale/empty Authorization header while the fresh login cookie is
    // already available. Prefer the valid cookie in that case so the first request is reliable.
    String token = bearerToken == null || bearerToken.isBlank() ? cookieToken : bearerToken;
    if (token != null) {
      if (jwtUtils.validateJwtToken(token)) {
        try {
          UserDetails user = users.loadUserByUsername(jwtUtils.getUserNameFromJwtToken(token));
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException | JwtException | IllegalArgumentException exception) {
          // Reject invalid credentials without logging bearer tokens or user details.
          SecurityContextHolder.clearContext();
        }
      } else if (cookieToken != null && !cookieToken.equals(token) && jwtUtils.validateJwtToken(cookieToken)) {
        authenticateCookie(request, cookieToken);
      }
    }
    chain.doFilter(request, response);
  }

  private String extractBearerToken(String header) {
    if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length()).trim();
  }

  private void authenticateCookie(HttpServletRequest request, String token) {
    try {
      UserDetails user = users.loadUserByUsername(jwtUtils.getUserNameFromJwtToken(token));
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (AuthenticationException | JwtException | IllegalArgumentException exception) {
      SecurityContextHolder.clearContext();
    }
  }
}
