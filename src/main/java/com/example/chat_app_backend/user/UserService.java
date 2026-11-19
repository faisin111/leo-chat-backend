package com.example.chat_app_backend.user;

import com.example.chat_app_backend.auth.session.RefreshSession;
import com.example.chat_app_backend.auth.session.RefreshSessionRepository;
import com.example.chat_app_backend.common.constants.AppConstants;
import com.example.chat_app_backend.common.dto.ApiMessage;
import com.example.chat_app_backend.common.enums.UserStatus;
import com.example.chat_app_backend.common.exception.BusinessRuleException;
import com.example.chat_app_backend.common.exception.NotFoundException;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.user.dto.UpdateProfileRequest;
import com.example.chat_app_backend.user.dto.UserProfileResponse;
import com.example.chat_app_backend.user.dto.UserSearchResponse;
import com.example.chat_app_backend.user.dto.UserSessionResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  @Autowired private UserRepository userRepository;
  @Autowired private RefreshSessionRepository refreshSessionRepository;

  /** Another user's profile as seen by {@code viewerId}: private contact fields are hidden. */
  public ResponseEntity<UserProfileResponse> getUserById(UUID id, UUID viewerId) {
    UserProfileResponse full = getUserById(id).getBody();
    if (full == null || id.equals(viewerId)) return ResponseEntity.ok(full);
    return ResponseEntity.ok(
        new UserProfileResponse(
            full.getId(),
            full.getUsername(),
            full.getDisplayName(),
            null,
            full.getRole(),
            full.getProfilePictureUrl(),
            null,
            full.getBio(),
            null,
            full.getRegion(),
            full.isEmailVerified()));
  }

  public ResponseEntity<UserProfileResponse> getUserById(UUID id) {
    Optional<User> userOpt = userRepository.findById(id);

    if (userOpt.isEmpty()) {
      throw new NotFoundException(AppConstants.ERR_USER_NOT_FOUND);
    }

    User user = userOpt.get();
    UserProfileResponse profile =
        new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getRole(),
            user.getProfile() != null ? user.getProfile().getProfilePictureUrl() : null,
            user.getProfile() != null ? user.getProfile().getPhoneNumber() : null,
            user.getProfile() != null ? user.getProfile().getBio() : null,
            user.getProfile() != null ? user.getProfile().getAge() : null,
            user.getProfile() != null ? user.getProfile().getRegion() : null,
            user.isEmailVerified());

    return ResponseEntity.ok(profile);
  }

  public ResponseEntity<UserProfileResponse> getCurrentUser() {
    UserDetailsImpl userDetails =
        (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return getUserById(userDetails.getId());
  }

  @Transactional
  public ResponseEntity<ApiMessage> updateProfile(UpdateProfileRequest request) {
    UserDetailsImpl userDetails =
        (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Optional<User> userOpt = userRepository.findById(userDetails.getId());

    if (userOpt.isEmpty()) {
      throw new NotFoundException(AppConstants.ERR_USER_NOT_FOUND);
    }

    User user = userOpt.get();
    Profile profile = user.getProfile();
    if (profile == null) {
      profile = new Profile(user);
      user.setProfile(profile);
    }

    if (request.profilePictureUrl() != null)
      profile.setProfilePictureUrl(request.profilePictureUrl());
    if (request.phoneNumber() != null) profile.setPhoneNumber(request.phoneNumber());
    if (request.bio() != null) profile.setBio(request.bio());
    if (request.age() != null) profile.setAge(request.age());
    if (request.region() != null) profile.setRegion(request.region());
    if (request.displayName() != null) user.setDisplayName(request.displayName());

    userRepository.save(user);
    return ResponseEntity.ok(new ApiMessage("Profile updated successfully"));
  }

  @Transactional
  public ResponseEntity<ApiMessage> deactivateCurrentUser() {
    UserDetailsImpl userDetails =
        (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    User user = userRepository.findById(userDetails.getId()).orElse(null);
    if (user == null) {
      throw new NotFoundException("Error: User not found.");
    }
    if (AppConstants.ROLE_ADMIN.equals(user.getRole())) {
      throw new BusinessRuleException("BUSINESS_RULE: Transfer ownership first");
    }
    user.setStatus(UserStatus.DISABLED.name());
    userRepository.save(user);

    return ResponseEntity.ok(new ApiMessage("Account deactivated successfully"));
  }

  public ResponseEntity<List<UserSearchResponse>> searchUsers(String query, int page, int size) {
    if (query == null || query.trim().length() < 2) {
      throw new BusinessRuleException("Search query must be at least 2 characters");
    }
    if (page < 0) {
      throw new BusinessRuleException("Page must be zero or greater");
    }
    if (size < 1 || size > 100) {
      throw new BusinessRuleException("Size must be between 1 and 100");
    }

    Pageable pageable = PageRequest.of(page, size);
    Page<User> users =
        userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            query.trim(), query.trim(), pageable);

    List<UserSearchResponse> responses =
        users.stream()
            .map(
                user ->
                    new UserSearchResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getLastSeenAt(),
                        user.getStatus(),
                        user.getProfile() != null ? user.getProfile().getProfilePictureUrl() : null,
                        user.getProfile() != null ? user.getProfile().getBio() : null,
                        user.getProfile() != null ? user.getProfile().getRegion() : null,
                        user.getProfile() != null ? user.getProfile().getAge() : null))
            .toList();

    return ResponseEntity.ok(responses);
  }

  public ResponseEntity<List<Map<String, Object>>> getPresence(List<UUID> ids) {
    List<User> users = userRepository.findAllById(ids);
    List<Map<String, Object>> presenceList =
        users.stream()
            .map(
                u -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("userId", u.getId());
                  map.put("lastSeenAt", u.getLastSeenAt());
                  map.put("status", u.getStatus());
                  return map;
                })
            .toList();
    return ResponseEntity.ok(presenceList);
  }

  public ResponseEntity<List<UserSessionResponse>> getSessions(UUID userId) {
    List<UserSessionResponse> sessions =
        refreshSessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toSessionResponse)
            .toList();
    return ResponseEntity.ok(sessions);
  }

  public ResponseEntity<ApiMessage> logoutSession(UUID userId, UUID sessionId) {
    RefreshSession session =
        refreshSessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));
    if (!userId.equals(session.getUserId())) throw new BusinessRuleException("Session not found");
    if (session.getLogoutAt() == null) session.setLogoutAt(java.time.Instant.now());
    refreshSessionRepository.save(session);
    return ResponseEntity.ok(new ApiMessage("Session logged out successfully."));
  }

  private UserSessionResponse toSessionResponse(RefreshSession session) {
    boolean active =
        session.getLogoutAt() == null && session.getExpiresAt().isAfter(java.time.Instant.now());
    return new UserSessionResponse(
        session.getId(),
        session.getCreatedAt(),
        session.getLastUsedAt(),
        session.getExpiresAt(),
        session.getLogoutAt(),
        session.getUserAgent(),
        session.getIpAddress(),
        session.getDeviceName(),
        session.getPlatform(),
        session.getAppVersion(),
        active);
  }
}
