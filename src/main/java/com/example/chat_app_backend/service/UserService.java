package com.example.chat_app_backend.service;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.payload.response.UserProfileResponse;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public ResponseEntity<?> getUserById(UUID id) {
        Optional<User> userOpt = userRepository.findById(id);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse(AppConstants.ERR_USER_NOT_FOUND));
        }

        User user = userOpt.get();
        UserProfileResponse profile = new UserProfileResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getProfile() != null ? user.getProfile().getProfilePictureUrl() : null,
                user.getProfile() != null ? user.getProfile().getPhoneNumber() : null,
                user.getProfile() != null ? user.getProfile().getBio() : null,
                user.getProfile() != null ? user.getProfile().getAge() : null,
                user.getProfile() != null ? user.getProfile().getRegion() : null
        );

        return ResponseEntity.ok(profile);
    }

    public ResponseEntity<?> getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return getUserById(userDetails.getId());
    }

    public ResponseEntity<?> updateProfile(com.example.chat_app_backend.payload.request.UpdateProfileRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> userOpt = userRepository.findById(userDetails.getId());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse(AppConstants.ERR_USER_NOT_FOUND));
        }

        User user = userOpt.get();
        com.example.chat_app_backend.model.Profile profile = user.getProfile();
        if (profile == null) {
            profile = new com.example.chat_app_backend.model.Profile(user);
            user.setProfile(profile);
        }
        
        if (request.profilePictureUrl() != null) profile.setProfilePictureUrl(request.profilePictureUrl());
        if (request.phoneNumber() != null) profile.setPhoneNumber(request.phoneNumber());
        if (request.bio() != null) profile.setBio(request.bio());
        if (request.age() != null) profile.setAge(request.age());
        if (request.region() != null) profile.setRegion(request.region());
        
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Profile updated successfully"));
    }
}
