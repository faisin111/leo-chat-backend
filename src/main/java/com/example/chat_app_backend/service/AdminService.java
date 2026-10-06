package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.model.Report;
import com.example.chat_app_backend.model.AuditLog;
import com.example.chat_app_backend.chat.conversation.Conversation;
import com.example.chat_app_backend.chat.message.Message;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.repository.ReportRepository;
import com.example.chat_app_backend.repository.AuditLogRepository;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.auth.AuthService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    return ResponseEntity.ok(java.util.Map.of(
        "totalUsers", totalUsers,
        "totalConversations", totalConversations,
        "totalReports", totalReports
    ));
  }

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<com.example.chat_app_backend.model.User>> getUsers(String q, String status, int cursor, int limit) {
    PageRequest pageRequest = PageRequest.of(cursor, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<User> usersPage;
    if (q != null && !q.isBlank()) {
        usersPage = userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(q, q, pageRequest);
    } else {
        usersPage = userRepository.findAll(pageRequest);
    }
    
    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            usersPage.getContent(), usersPage.hasNext(), usersPage.hasNext() ? String.valueOf(cursor + 1) : null));
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
    if ("ROLE_ADMIN".equals(user.getRole())) {
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
    if ("ROLE_ADMIN".equals(user.getRole())) {
      throw new com.example.chat_app_backend.exception.ForbiddenException(
          "Error: Cannot target admin.");
    }

    user.setStatus("DELETED");
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

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<Conversation>> getConversations(String q, String status, int cursor) {
    PageRequest pageRequest = PageRequest.of(cursor, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Conversation> page = conversationRepository.findAll(pageRequest);
    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            page.getContent(), page.hasNext(), page.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  @Transactional
  public ResponseEntity<MessageResponse> updateConversationStatus(
      UUID id,
      com.example.chat_app_backend.payload.request.UpdateConversationStatusRequest request) {
    Conversation conv = conversationRepository.findById(id)
        .orElseThrow(() -> new com.example.chat_app_backend.exception.NotFoundException("Conversation not found"));
    conv.setStatus(request.status());
    conversationRepository.save(conv);
    return ResponseEntity.ok(new MessageResponse("Conversation status updated successfully."));
  }

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<Report>> getReports(String status, int cursor) {
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
    Report report = reportRepository.findById(id)
        .orElseThrow(() -> new com.example.chat_app_backend.exception.NotFoundException("Report not found"));
    return ResponseEntity.ok(report);
  }

  @Transactional
  public ResponseEntity<MessageResponse> resolveReport(
      UUID id, com.example.chat_app_backend.payload.request.ResolveReportRequest request) {
    Report report = reportRepository.findById(id)
        .orElseThrow(() -> new com.example.chat_app_backend.exception.NotFoundException("Report not found"));
    
    report.setStatus("RESOLVED");
    report.setResolution(request.resolution());
    report.setResolvedAt(java.time.Instant.now());
    reportRepository.save(report);
    
    return ResponseEntity.ok(new MessageResponse("Report resolved successfully."));
  }

  @Transactional
  public ResponseEntity<MessageResponse> deleteMessage(UUID id, String reason) {
    Message message = messageRepository.findById(id)
        .orElseThrow(() -> new com.example.chat_app_backend.exception.NotFoundException("Message not found"));
    
    message.setRemovedByAdmin(true);
    message.setRemovalReason(reason);
    message.setDeletedAt(java.time.Instant.now());
    message.setContent("This message was removed by an admin.");
    messageRepository.save(message);
    
    return ResponseEntity.ok(new MessageResponse("Message deleted successfully."));
  }

  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<AuditLog>> getAuditLogs(
      UUID actor, String action, String from, String to, int cursor) {
    PageRequest pageRequest = PageRequest.of(cursor, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<AuditLog> page;
    
    if (actor != null && action != null && !action.isBlank()) {
        page = auditLogRepository.findByActorIdAndAction(actor, action, pageRequest);
    } else if (actor != null) {
        page = auditLogRepository.findByActorId(actor, pageRequest);
    } else if (action != null && !action.isBlank()) {
        page = auditLogRepository.findByAction(action, pageRequest);
    } else {
        page = auditLogRepository.findAll(pageRequest);
    }
    
    return ResponseEntity.ok(
        new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
            page.getContent(), page.hasNext(), page.hasNext() ? String.valueOf(cursor + 1) : null));
  }

  @Transactional
  public ResponseEntity<MessageResponse> transferOwnership(
      com.example.chat_app_backend.payload.request.TransferOwnershipRequest request) {
      
      User targetUser = userRepository.findById(request.targetUserId())
          .orElseThrow(() -> new com.example.chat_app_backend.exception.NotFoundException("Target user not found"));
          
      targetUser.setRole("ROLE_ADMIN");
      userRepository.save(targetUser);
      
      return ResponseEntity.ok(new MessageResponse("Ownership transferred successfully."));
  }
}
