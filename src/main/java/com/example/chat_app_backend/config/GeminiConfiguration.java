package com.example.chat_app_backend.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfiguration {

  @Bean
  @ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
  Client geminiClient(@Value("${app.ai.api-key:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("GEMINI_API_KEY is required when GEMINI_ENABLED=true");
    }
    return Client.builder().apiKey(apiKey).build();
  }
}
