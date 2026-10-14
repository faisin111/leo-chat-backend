package com.example.chat_app_backend.config.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.supabase")
public record SupabaseStorageProperties(
    boolean enabled,
    @NotBlank String endpoint,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    @Min(60) @Max(3600) long uploadExpirySeconds,
    @Min(60) @Max(3600) long downloadExpirySeconds,
    @Min(1) @Max(52428800) long maxImageBytes) {

  public URI endpointUri() {
    return URI.create(endpoint);
  }
}
