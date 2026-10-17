package com.example.chat_app_backend.service;

import com.example.chat_app_backend.auth.AuthService;
import com.example.chat_app_backend.chat.conversation.Conversation;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.message.Message;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.common.constants.AppConstants;
import com.example.chat_app_backend.common.enums.ReportStatus;
import com.example.chat_app_backend.common.enums.UserStatus;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.model.AuditLog;
import com.example.chat_app_backend.model.Report;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.response.AdminUserResponse;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.repository.AuditLogRepository;
import com.example.chat_app_backend.repository.ReportRepository;
import com.example.chat_app_backend.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

  @Autowired private UserRepository userRepository;
  @Autowired private ReportRepository reportRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private ConversationRepository conversationRepository;
  @Autowired private MessageRepository messageRepository;
  @Autowired private AuthService authService;
  @Autowired private PasswordEncoder passwordEncoder;

  public ResponseEntity<java.util.Map<String, Object>> getStatsOverview() {
    long totalUsers = userRepository.count();
    long totalConversations = conversationRepository.count();
    long totalReports = reportRepository.count();

    return ResponseEntity.ok(
        java.util.Map.of(
            "totalUsers", totalUsers,
            "totalConversations", totalConversations,
            "totalReports", totalReports));
  }

  public ResponseEntity<
          com.example.chat_app_backend.payload.response.CursorPageResponse<AdminUserResponse>>
      getUsers(String q, String status, int cursor, int limit) {
    validatePage(cursor, limit);
    PageRequest pageRequest =
        PageRequest.of(cursor, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
    String normalizedQuery = q == null || q.isBlank() ? null : q.trim();
    Page<User> usersPage =
        normalizedStatus == null && normalizedQuery == null
            ? userRepository.findAll(pageRequest)
            : userRepository.searchAdminUsers(normalizedStatus, normalizedQuery, pageRequest);

    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            usersPage.getContent().stream().map(this::toAdminUser).toList(),
            usersPage.hasNext(),
            usersPage.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  public ResponseEntity<java.util.Map<String, Object>> getUserById(UUID id) {
    Optional<User> userOpt = userRepository.findById(id);
    if (userOpt.isEmpty()) {
      throw new com.example.chat_app_backend.exception.NotFoundException("Error: User not found.");
    }
    User user = userOpt.get();
    return ResponseEntity.ok(
        java.util.Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "role", user.getRole(),
            "displayName", user.getDisplayName(),
            "status", user.getStatus()));
  }

  @Transactional
  public ResponseEntity<MessageResponse> updateUserStatus(
      UUID id, com.example.chat_app_backend.payload.request.UpdateUserStatusRequest request) {
    Optional<User> userOpt = userRepository.findById(id);
    if (userOpt.isEmpty()) {
      throw new com.example.chat_app_backend.exception.NotFoundException("Error: User not found.");
    }
    User user = userOpt.get();
    if (AppConstants.ROLE_ADMIN.equals(user.getRole())) {
      throw new com.example.chat_app_backend.exception.ForbiddenException(
          "Error: Cannot target admin.");
    }
    user.setStatus(request.status());
    user.setStatusReason(request.reason());
    user.setStatusChangedAt(java.time.Instant.now());
    userRepository.save(user);
    return ResponseEntity.ok(new MessageResponse("User status updated successfully."));
  }

  @Transactional
  public ResponseEntity<MessageResponse> forceLogout(UUID id) {
    authService.logoutAll(id);
    return ResponseEntity.ok(new MessageResponse("User forcefully logged out from all sessions."));
  }

  @Transactional
  public ResponseEntity<MessageResponse> deleteUser(UUID id) {
    Optional<User> userOpt = userRepository.findById(id);
    if (userOpt.isEmpty()) {
      throw new com.example.chat_app_backend.exception.NotFoundException("Error: User not found.");
    }
    User user = userOpt.get();
    if (AppConstants.ROLE_ADMIN.equals(user.getRole())) {
      throw new com.example.chat_app_backend.exception.ForbiddenException(
          "Error: Cannot target admin.");
    }

    user.setStatus(UserStatus.DELETED.name());
    user.setUsername("deleted_" + UUID.randomUUID().toString().substring(0, 8));
    user.setEmail("deleted_" + UUID.randomUUID().toString().substring(0, 8) + "@deleted.com");
    if (user.getProfile() != null) {
      user.getProfile().setBio(null);
      user.getProfile().setPhoneNumber(null);
      user.getProfile().setProfilePictureUrl(null);
      user.getProfile().setAge(null);
      user.getProfile().setRegion(null);
    }
    userRepository.save(user);

    return ResponseEntity.ok(
        new MessageResponse("User anonymized and softly deleted successfully."));
  }

  public ResponseEntity<
          com.example.chat_app_backend.payload.response.CursorPageResponse<Conversation>>
      getConversations(String q, String status, int cursor) {
    validatePage(cursor, 50);
    PageRequest pageRequest = PageRequest.of(cursor, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
    String normalizedQuery = q == null || q.isBlank() ? null : q.trim();
    Page<Conversation> page =
        normalizedStatus == null && normalizedQuery == null
            ? conversationRepository.findAll(pageRequest)
            : conversationRepository.searchAdminConversations(
                normalizedStatus, normalizedQuery, pageRequest);
    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            page.getContent(), page.hasNext(), page.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  @Transactional
  public ResponseEntity<MessageResponse> updateConversationStatus(
      UUID id,
      com.example.chat_app_backend.payload.request.UpdateConversationStatusRequest request) {
    Conversation conv =
        conversationRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new com.example.chat_app_backend.exception.NotFoundException(
                        "Conversation not found"));
    conv.setStatus(request.status());
    conversationRepository.save(conv);
    return ResponseEntity.ok(new MessageResponse("Conversation status updated successfully."));
  }

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<Report>>
      getReports(String status, int cursor) {
    validatePage(cursor, 50);
    PageRequest pageRequest = PageRequest.of(cursor, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Report> page;
    if (status != null && !status.isBlank()) {
      page = reportRepository.findByStatus(status, pageRequest);
    } else {
      page = reportRepository.findAll(pageRequest);
    }
    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            page.getContent(), page.hasNext(), page.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  public ResponseEntity<Report> getReportDetail(UUID id) {
    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new com.example.chat_app_backend.exception.NotFoundException(
                        "Report not found"));
    return ResponseEntity.ok(report);
  }

  @Transactional
  public ResponseEntity<MessageResponse> resolveReport(
      UUID id, com.example.chat_app_backend.payload.request.ResolveReportRequest request) {
    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new com.example.chat_app_backend.exception.NotFoundException(
                        "Report not found"));

    report.setStatus(ReportStatus.RESOLVED.name());
    report.setResolution(request.resolution());
    report.setResolvedAt(java.time.Instant.now());
    reportRepository.save(report);

    return ResponseEntity.ok(new MessageResponse("Report resolved successfully."));
  }

  @Transactional
  public ResponseEntity<MessageResponse> deleteMessage(UUID id, String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new BusinessRuleException("A removal reason of 1 to 500 characters is required");
    }
    Message message =
        messageRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new com.example.chat_app_backend.exception.NotFoundException(
                        "Message not found"));

    message.setRemovedByAdmin(true);
    message.setRemovalReason(reason);
    message.setDeletedAt(java.time.Instant.now());
    message.setContent("This message was removed by an admin.");
    messageRepository.save(message);

    return ResponseEntity.ok(new MessageResponse("Message deleted successfully."));
  }

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<AuditLog>>
      getAuditLogs(UUID actor, String action, String from, String to, int cursor) {
    validatePage(cursor, 50);
    PageRequest pageRequest = PageRequest.of(cursor, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    Instant fromInstant = parseInstant(from, "from");
    Instant toInstant = parseInstant(to, "to");
    if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant))
      throw new BusinessRuleException("from must be before or equal to to");
    String normalizedAction = action == null || action.isBlank() ? null : action.trim();
    Page<AuditLog> page;
    if (fromInstant == null && toInstant == null) {
      if (actor != null && normalizedAction != null)
        page = auditLogRepository.findByActorIdAndAction(actor, normalizedAction, pageRequest);
      else if (actor != null) page = auditLogRepository.findByActorId(actor, pageRequest);
      else if (normalizedAction != null)
        page = auditLogRepository.findByAction(normalizedAction, pageRequest);
      else page = auditLogRepository.findAll(pageRequest);
    } else {
      page =
          auditLogRepository.findFiltered(
              actor, normalizedAction, fromInstant, toInstant, pageRequest);
    }

    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            page.getContent(), page.hasNext(), page.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  @Transactional
  public ResponseEntity<MessageResponse> transferOwnership(
      UUID actorId, com.example.chat_app_backend.payload.request.TransferOwnershipRequest request) {
    User actor =
        userRepository
            .findById(actorId)
            .orElseThrow(() -> new ForbiddenException("Active admin account required"));
    if (!AppConstants.ROLE_ADMIN.equals(actor.getRole())
        || !passwordEncoder.matches(request.password(), actor.getPasswordHash())) {
      throw new ForbiddenException("Current admin credentials are invalid");
    }
    User targetUser =
        userRepository
            .findById(request.targetUserId())
            .orElseThrow(
                () ->
                    new com.example.chat_app_backend.exception.NotFoundException(
                        "Target user not found"));
    if (actorId.equals(targetUser.getId())) {
      throw new BusinessRuleException("Target must be a different user");
    }
    if (!UserStatus.ACTIVE.name().equals(targetUser.getStatus())) {
      throw new BusinessRuleException("Target user must be active");
    }
    if (AppConstants.ROLE_ADMIN.equals(targetUser.getRole())) {
      throw new BusinessRuleException("Target user is already an admin");
    }
    actor.setRole(AppConstants.ROLE_USER);
    userRepository.saveAndFlush(actor);
    targetUser.setRole(AppConstants.ROLE_ADMIN);
    userRepository.save(targetUser);

    return ResponseEntity.ok(new MessageResponse("Ownership transferred successfully."));
  }

  private void validatePage(int cursor, int limit) {
    if (cursor < 0) throw new BusinessRuleException("Cursor must be zero or greater");
    if (limit < 1 || limit > 100)
      throw new BusinessRuleException("Limit must be between 1 and 100");
  }

  private AdminUserResponse toAdminUser(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole(),
        user.getStatus(),
        user.isMustChangePassword(),
        user.isEmailVerified(),
        user.getLastSeenAt(),
        user.getStatusReason(),
        user.getStatusChangedAt(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private Instant parseInstant(String value, String name) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (java.time.format.DateTimeParseException ex) {
      throw new BusinessRuleException(name + " must be an ISO-8601 timestamp");
    }
  }
}
