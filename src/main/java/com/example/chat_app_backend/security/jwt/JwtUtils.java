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

  @Value("${app.jwt.jwtRefreshExpirationMs:604800000}")
  private long jwtRefreshExpirationMs;

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
        .maxAge(jwtExpirationMs / 1000)
        .httpOnly(true)
        .secure(false)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie generateRefreshJwtCookie(String refreshToken) {
    return ResponseCookie.from(jwtRefreshCookie, refreshToken)
        .path("/api/v1/auth/refresh")
        .maxAge(jwtRefreshExpirationMs / 1000)
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
        .claim("type", "access")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateRefreshToken(String username) {
    return Jwts.builder()
        .setSubject(username)
        .claim("type", "refresh")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + jwtRefreshExpirationMs))
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
      var claims =
          Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken).getBody();
      Object type = claims.get("type");
      return type == null || "access".equals(type);
    } catch (Exception e) {
      // Log exception
    }
    return false;
  }

  public boolean validateRefreshToken(String authToken) {
    try {
      var claims =
          Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken).getBody();
      return "refresh".equals(claims.get("type"));
    } catch (Exception e) {
      // Log exception
    }
    return false;
  }
}
