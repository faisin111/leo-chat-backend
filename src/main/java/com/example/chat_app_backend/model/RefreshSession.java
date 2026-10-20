package com.example.chat_app_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant lastUsedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant logoutAt;

  @Column(length = 500)
  private String userAgent;

  @Column(length = 100)
  private String ipAddress;

  @Column(length = 120)
  private String deviceName;

  @Column(length = 40)
  private String platform;

  @Column(length = 40)
  private String appVersion;

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getLogoutAt() { return logoutAt; }
  public void setLogoutAt(Instant logoutAt) { this.logoutAt = logoutAt; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
  public String getDeviceName() { return deviceName; }
  public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
  public String getPlatform() { return platform; }
  public void setPlatform(String platform) { this.platform = platform; }
  public String getAppVersion() { return appVersion; }
  public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
}
