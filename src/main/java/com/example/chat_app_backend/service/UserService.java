package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.payload.response.UserProfileResponse;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  @Autowired private UserRepository userRepository;

  public ResponseEntity<UserProfileResponse> getUserById(UUID id) {
    Optional<User> userOpt = userRepository.findById(id);

    if (userOpt.isEmpty()) {
      throw new com.example.chat_app_backend.exception.NotFoundException(
          AppConstants.ERR_USER_NOT_FOUND);
    }

    User user = userOpt.get();
    UserProfileResponse profile =
        new UserProfileResponse(
            user.getId(),
            user.getUsername(),
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

  public ResponseEntity<MessageResponse> updateProfile(
      com.example.chat_app_backend.payload.request.UpdateProfileRequest request) {
    UserDetailsImpl userDetails =
        (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Optional<User> userOpt = userRepository.findById(userDetails.getId());

    if (userOpt.isEmpty()) {
      throw new com.example.chat_app_backend.exception.NotFoundException(
          AppConstants.ERR_USER_NOT_FOUND);
    }

    User user = userOpt.get();
    com.example.chat_app_backend.model.Profile profile = user.getProfile();
    if (profile == null) {
      profile = new com.example.chat_app_backend.model.Profile(user);
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
    return ResponseEntity.ok(new MessageResponse("Profile updated successfully"));
  }

  @Transactional
  public ResponseEntity<MessageResponse> deactivateCurrentUser() {
    UserDetailsImpl userDetails =
        (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    User user = userRepository.findById(userDetails.getId()).orElse(null);
    if (user == null) {
      throw new com.example.chat_app_backend.exception.NotFoundException("Error: User not found.");
    }
    if ("ADMIN".equals(user.getRole())) {
      throw new com.example.chat_app_backend.exception.BusinessRuleException(
          "BUSINESS_RULE: Transfer ownership first");
    }
    user.setStatus("DISABLED");
    userRepository.save(user);

    return ResponseEntity.ok(new MessageResponse("Account deactivated successfully"));
  }

  public ResponseEntity<List<com.example.chat_app_backend.payload.response.UserSearchResponse>> searchUsers(String query, int page, int size) {
    if (query == null || query.trim().length() < 2) {
      throw new com.example.chat_app_backend.exception.BusinessRuleException(
          "Search query must be at least 2 characters");
    }

    org.springframework.data.domain.Pageable pageable =
        org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<User> users =
        userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            query.trim(), query.trim(), pageable);

    List<com.example.chat_app_backend.payload.response.UserSearchResponse> responses =
        users.stream()
            .map(
                user ->
                    new com.example.chat_app_backend.payload.response.UserSearchResponse(
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

  public ResponseEntity<List<java.util.Map<String, Object>>> getPresence(List<UUID> ids) {
    List<User> users = userRepository.findAllById(ids);
    List<java.util.Map<String, Object>> presenceList =
        users.stream()
            .map(
                u -> {
                  java.util.Map<String, Object> map = new java.util.HashMap<>();
                  map.put("userId", u.getId());
                  map.put("lastSeenAt", u.getLastSeenAt());
                  map.put("status", u.getStatus());
                  return map;
                })
            .toList();
    return ResponseEntity.ok(presenceList);
  }
  
  public ResponseEntity<List<java.util.Map<String, Object>>> getSessions(UUID userId) {
    // Pure stateless: sessions are not stored in database
    return ResponseEntity.ok(List.of());
  }

  public ResponseEntity<MessageResponse> logoutSession(UUID userId, UUID sessionId) {
    // Pure stateless: individual sessions are not stored in database
    return ResponseEntity.ok(new MessageResponse("Session logged out successfully"));
  }
}
