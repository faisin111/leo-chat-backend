package com.example.chat_app_backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String formspreeEndpoint, String publicUrl, String fromAddress) {}
