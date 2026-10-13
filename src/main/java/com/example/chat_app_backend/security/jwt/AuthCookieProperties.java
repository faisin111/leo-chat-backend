package com.example.chat_app_backend.security.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.cookies")
public record AuthCookieProperties(
    @DefaultValue("true") boolean secure,
    @NotBlank @Pattern(regexp = "Lax|Strict|None") @DefaultValue("Lax") String sameSite,
    @NotBlank @DefaultValue("leo_chat_jwt") String accessName,
    @NotBlank @DefaultValue("leo_chat_jwt_refresh") String refreshName) {
  @AssertTrue(message = "SameSite=None requires Secure cookies")
  public boolean isSecureForSameSite() {
    return !"None".equals(sameSite) || secure;
  }
}
