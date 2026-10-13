package com.example.chat_app_backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {

  private static final String TYPE = "type";
  private static final String ACCESS = "access";
  private static final String REFRESH = "refresh";
  private final JwtProperties properties;
  private final Key signingKey;

  public JwtUtils(JwtProperties properties) {
    this.properties = properties;
    this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
  }

  private JwtBuilder tokenBuilder(String subject) {
    return Jwts.builder()
        .setSubject(subject)
        .setIssuer(properties.issuer())
        .setAudience(properties.audience())
        .setId(UUID.randomUUID().toString())
        .setIssuedAt(new Date())
        .signWith(signingKey, SignatureAlgorithm.HS256);
  }

  public String generateTokenFromUsername(String username) {
    return tokenBuilder(username)
        .claim(TYPE, ACCESS)
        .setExpiration(new Date(System.currentTimeMillis() + properties.expirationMs()))
        .compact();
  }

  public String generateRefreshToken(String username) {
    return generateRefreshToken(
        username, Instant.now().plusMillis(properties.jwtRefreshExpirationMs()));
  }

  public String generateRefreshToken(String username, Instant expiresAt) {
    return tokenBuilder(username)
        .claim(TYPE, REFRESH)
        .setExpiration(Date.from(expiresAt))
        .compact();
  }

  public long accessExpiresInSeconds() {
    return properties.expirationMs() / 1000;
  }

  public long refreshExpiresInSeconds() {
    return properties.jwtRefreshExpirationMs() / 1000;
  }

  private Key key() {
    return signingKey;
  }

  private Claims claims(String token) {
    Claims claims =
        Jwts.parserBuilder()
            .setSigningKey(key())
            .requireIssuer(properties.issuer())
            .requireAudience(properties.audience())
            .build()
            .parseClaimsJws(token)
            .getBody();
    if (claims.getSubject() == null
        || claims.getSubject().isBlank()
        || claims.getExpiration() == null
        || claims.getIssuedAt() == null
        || claims.getId() == null) {
      throw new MalformedJwtException("Missing required token claims");
    }
    return claims;
  }

  public String getUserNameFromJwtToken(String token) {
    return claims(token).getSubject();
  }

  public Instant refreshExpiresAt(String token) {
    return claims(token).getExpiration().toInstant();
  }

  public boolean validateJwtToken(String token) {
    return validType(token, ACCESS);
  }

  public boolean validateRefreshToken(String token) {
    return validType(token, REFRESH);
  }

  private boolean validType(String token, String expectedType) {
    try {
      return expectedType.equals(claims(token).get(TYPE));
    } catch (JwtException | IllegalArgumentException exception) {
      return false;
    }
  }

  public String generateEmailVerificationToken(String email) {
    return tokenBuilder(email)
        .claim("type", "email_verification")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000L)) // 24 hours
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  public String getEmailFromVerificationToken(String token) {
    return claims(token).getSubject();
  }

  public boolean validateEmailVerificationToken(String token) {
    try {
      var claims = claims(token);
      return "email_verification".equals(claims.get("type"));
    } catch (Exception e) {
      // Log exception
    }
    return false;
  }

  public String generatePasswordResetToken(String email, String passwordHash) {
    String hashFingerprint =
        passwordHash.length() > 10
            ? passwordHash.substring(passwordHash.length() - 10)
            : passwordHash;
    return tokenBuilder(email)
        .claim("type", "password_reset")
        .claim("ph", hashFingerprint)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 3600000L)) // 1 hour
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  public String getEmailFromPasswordResetToken(String token) {
    return claims(token).getSubject();
  }

  public boolean validatePasswordResetToken(String token, String currentPasswordHash) {
    try {
      var claims = claims(token);
      if (!"password_reset".equals(claims.get("type"))) {
        return false;
      }
      String hashFingerprint =
          currentPasswordHash.length() > 10
              ? currentPasswordHash.substring(currentPasswordHash.length() - 10)
              : currentPasswordHash;
      return hashFingerprint.equals(claims.get("ph"));
    } catch (Exception e) {
      // Log exception
    }
    return false;
  }
}
