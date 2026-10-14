package com.example.chat_app_backend.config.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
  S3Client supabaseS3Client(SupabaseStorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .forcePathStyle(true)
        .credentialsProvider(credentials(properties))
        .build();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
  S3Presigner supabaseS3Presigner(SupabaseStorageProperties properties) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .credentialsProvider(credentials(properties))
        .build();
  }

  private StaticCredentialsProvider credentials(SupabaseStorageProperties properties) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
  }
}
