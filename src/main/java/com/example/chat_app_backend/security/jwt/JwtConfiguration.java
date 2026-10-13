package com.example.chat_app_backend.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class})
public class JwtConfiguration {}
