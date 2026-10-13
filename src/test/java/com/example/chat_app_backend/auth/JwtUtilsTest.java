package com.example.chat_app_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.chat_app_backend.security.jwt.JwtProperties;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtUtilsTest {
  private static final String SECRET =
      "9a3b68f1c84d63a8e32f5d94719280d463b2c918a5e8f572a1d374c8b928374a";
  JwtUtils jwt = new JwtUtils(new JwtProperties(SECRET, 900000, 604800000, "issuer", "audience"));

  String signed(String type, String issuer, String audience, Instant expiry) {
    return Jwts.builder()
        .setSubject("alice")
        .setId("id")
        .setIssuedAt(new Date())
        .setExpiration(Date.from(expiry))
        .setIssuer(issuer)
        .setAudience(audience)
        .claim("type", type)
        .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
        .compact();
  }

  @Test
  void issuedTokensAreUniqueAndTypesCannotBeSwapped() {
    String access = jwt.generateTokenFromUsername("alice"),
        refresh = jwt.generateRefreshToken("alice");
    assertThat(jwt.validateJwtToken(access)).isTrue();
    assertThat(jwt.validateRefreshToken(refresh)).isTrue();
    assertThat(jwt.generateTokenFromUsername("alice")).isNotEqualTo(access);
    assertThat(jwt.validateJwtToken(refresh)).isFalse();
    assertThat(jwt.validateRefreshToken(access)).isFalse();
    assertThat(jwt.validateJwtToken(jwt.generateEmailVerificationToken("a@test.com"))).isFalse();
    assertThat(jwt.validateJwtToken(jwt.generatePasswordResetToken("a@test.com", "hash")))
        .isFalse();
  }

  @Test
  void rejectsExpiredTamperedWrongIssuerAudienceAndMissingType() {
    Instant future = Instant.now().plusSeconds(100);
    assertThat(
            jwt.validateJwtToken(
                signed("access", "issuer", "audience", Instant.now().minusSeconds(10))))
        .isFalse();
    assertThat(jwt.validateJwtToken(signed("access", "wrong", "audience", future))).isFalse();
    assertThat(jwt.validateJwtToken(signed("access", "issuer", "wrong", future))).isFalse();
    assertThat(jwt.validateJwtToken(signed(null, "issuer", "audience", future))).isFalse();
    String token = jwt.generateTokenFromUsername("alice");
    assertThat(jwt.validateJwtToken("x" + token.substring(1))).isFalse();
    assertThat(jwt.validateJwtToken("invalid")).isFalse();
  }
}
