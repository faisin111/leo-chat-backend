package com.example.chat_app_backend.security.jwt;

import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
public class JwtUtils {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.expirationMs}")
  private int jwtExpirationMs;

  @Value("${app.jwt.jwtCookieName:leo_chat_jwt}")
  private String jwtCookie;

  @Value("${app.jwt.jwtRefreshCookieName:leo_chat_jwt_refresh}")
  private String jwtRefreshCookie;

  public String getJwtFromCookies(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, jwtCookie);
    if (cookie != null) {
      return cookie.getValue();
    } else {
      return null;
    }
  }

  public String getJwtRefreshFromCookies(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, jwtRefreshCookie);
    if (cookie != null) {
      return cookie.getValue();
    } else {
      return null;
    }
  }

  public ResponseCookie generateJwtCookie(String jwt) {
    return ResponseCookie.from(jwtCookie, jwt)
        .path("/api")
        .maxAge(24 * 60 * 60)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie generateRefreshJwtCookie(String refreshToken) {
    return ResponseCookie.from(jwtRefreshCookie, refreshToken)
        .path("/api/v1/auth/refresh")
        .maxAge(24 * 60 * 60)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie getCleanJwtCookie() {
    return ResponseCookie.from(jwtCookie, "").path("/api").maxAge(0).build();
  }

  public ResponseCookie getCleanJwtRefreshCookie() {
    return ResponseCookie.from(jwtRefreshCookie, "")
        .path("/api/v1/auth/refresh")
        .maxAge(0)
        .build();
  }

  public String generateJwtToken(Authentication authentication) {
    UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
    return generateTokenFromUsername(userPrincipal.getUsername());
  }

  public String generateTokenFromUsername(String username) {
    return Jwts.builder()
        .setSubject(username)
        .setIssuedAt(new Date())
        .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  private Key key() {
    return Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret));
  }

  public String getUserNameFromJwtToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(key())
        .build()
        .parseClaimsJws(token)
        .getBody()
        .getSubject();
  }

  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
      return true;
    } catch (Exception e) {
      // Log exception
    }
    return false;
  }
}
