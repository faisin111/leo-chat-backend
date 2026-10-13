package com.example.chat_app_backend.auth.bootstrap;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.admin.bootstrap")
public record AdminBootstrapProperties(
    @NotBlank @Size(min = 3, max = 20) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 12, max = 40) String password) {}
