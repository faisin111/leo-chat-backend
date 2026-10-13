package com.example.chat_app_backend.security.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    @NotBlank String secret,
    @Min(1000) @DefaultValue("900000") long expirationMs,
    @Min(1000) @DefaultValue("604800000") long jwtRefreshExpirationMs,
    @NotBlank @DefaultValue("leo-chat-api") String issuer,
    @NotBlank @DefaultValue("leo-chat-clients") String audience) {}
