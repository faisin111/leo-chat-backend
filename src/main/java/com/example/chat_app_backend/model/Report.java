package com.example.chat_app_backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "reports",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"reporterId", "targetType", "targetId"})})
public class Report {
  @Id private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID reporterId;

  @Column(nullable = false, length = 10)
  private String targetType; // MESSAGE | USER

  @Column(nullable = false)
  private UUID targetId;

  @Column(nullable = false, length = 30)
  private String reason; // SPAM | HARASSMENT | ABUSE | OTHER

  @Column(length = 500)
  private String details;

  @Column(nullable = false, length = 12)
  private String status = "OPEN"; // OPEN | RESOLVED | DISMISSED

  private UUID resolvedBy;

  @Column(length = 300)
  private String resolution;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  private Instant resolvedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getReporterId() {
    return reporterId;
  }

  public void setReporterId(UUID reporterId) {
    this.reporterId = reporterId;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public void setTargetId(UUID targetId) {
    this.targetId = targetId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(UUID resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public String getResolution() {
    return resolution;
  }

  public void setResolution(String resolution) {
    this.resolution = resolution;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }
}
