package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.request.AdminCreateUserRequest;
import com.example.chat_app_backend.payload.request.AdminUpdateUserRequest;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.payload.response.UserProfileResponse;
import com.example.chat_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    
    public ResponseEntity<?> getStatsOverview() {
        return ResponseEntity.ok(java.util.Map.of("message", "Stats not implemented"));
    }

    public ResponseEntity<?> getUsers(String q, String status, int cursor, int limit) {
        return ResponseEntity.ok(java.util.Map.of("message", "Filter users not implemented"));
    }

    public ResponseEntity<?> getUserById(UUID id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        User user = userOpt.get();
                return ResponseEntity.ok(java.util.Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "displayName", user.getDisplayName(),
                "status", user.getStatus()
        ));
    }

    @Transactional
    public ResponseEntity<?> updateUserStatus(UUID id, com.example.chat_app_backend.payload.request.UpdateUserStatusRequest request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        User user = userOpt.get();
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Cannot target admin."));
        }
        user.setStatus(request.status());
        user.setStatusReason(request.reason());
        user.setStatusChangedAt(java.time.Instant.now());
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User status updated successfully."));
    }

    @Transactional
    public ResponseEntity<?> forceLogout(UUID id) {
        return ResponseEntity.ok(new MessageResponse("Force logout not implemented."));
    }

    @Transactional
    public ResponseEntity<?> deleteUser(UUID id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        User user = userOpt.get();
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Cannot target admin."));
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
        
        return ResponseEntity.ok(new MessageResponse("User anonymized and softly deleted successfully."));
    }

    public ResponseEntity<?> getConversations(String q, String status, int cursor) {
        return ResponseEntity.ok(java.util.Map.of("message", "List conversations not implemented"));
    }

    @Transactional
    public ResponseEntity<?> updateConversationStatus(UUID id, com.example.chat_app_backend.payload.request.UpdateConversationStatusRequest request) {
        return ResponseEntity.ok(new MessageResponse("Update conversation status not implemented."));
    }

    public ResponseEntity<?> getReports(String status, int cursor) {
        return ResponseEntity.ok(java.util.Map.of("message", "List reports not implemented"));
    }

    public ResponseEntity<?> getReportDetail(UUID id) {
        return ResponseEntity.ok(java.util.Map.of("message", "Report detail not implemented"));
    }

    @Transactional
    public ResponseEntity<?> resolveReport(UUID id, com.example.chat_app_backend.payload.request.ResolveReportRequest request) {
        return ResponseEntity.ok(new MessageResponse("Resolve report not implemented."));
    }

    @Transactional
    public ResponseEntity<?> deleteMessage(UUID id, String reason) {
        return ResponseEntity.ok(new MessageResponse("Delete message not implemented."));
    }

    public ResponseEntity<?> getAuditLogs(UUID actor, String action, String from, String to, int cursor) {
        return ResponseEntity.ok(java.util.Map.of("message", "Audit logs not implemented"));
    }

    @Transactional
    public ResponseEntity<?> transferOwnership(com.example.chat_app_backend.payload.request.TransferOwnershipRequest request) {
        // Find current user doing the transfer
        // Note: For simplicity we assume it's valid if they reached here (due to hasRole(ADMIN)).
        return ResponseEntity.ok(new MessageResponse("Transfer ownership not implemented."));
    }
}
