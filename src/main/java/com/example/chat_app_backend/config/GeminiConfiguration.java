package com.example.chat_app_backend.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfiguration {

  @Bean
  @ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
  Client geminiClient(
      @Value("${app.ai.api-key:}") String apiKey,
      @Value("${app.ai.timeout-ms:25000}") int timeoutMs) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("GEMINI_API_KEY is required when GEMINI_ENABLED=true");
    }
    return Client.builder()
        .apiKey(apiKey)
        .httpOptions(
            HttpOptions.builder()
                .timeout(timeoutMs)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build())
        .build();
  }
}
