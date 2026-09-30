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

    public ResponseEntity<?> getAllUsers() {
        List<UserProfileResponse> users = userRepository.findAll().stream()
                .map(user -> new UserProfileResponse(
                        user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                        user.getProfile() != null ? user.getProfile().getProfilePictureUrl() : null,
                        user.getProfile() != null ? user.getProfile().getPhoneNumber() : null,
                        user.getProfile() != null ? user.getProfile().getBio() : null,
                        user.getProfile() != null ? user.getProfile().getAge() : null,
                        user.getProfile() != null ? user.getProfile().getRegion() : null
                ))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(users);
    }


    public ResponseEntity<?> getUserById(UUID id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        User user = userOpt.get();
        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getProfile() != null ? user.getProfile().getProfilePictureUrl() : null,
                user.getProfile() != null ? user.getProfile().getPhoneNumber() : null,
                user.getProfile() != null ? user.getProfile().getBio() : null,
                user.getProfile() != null ? user.getProfile().getAge() : null,
                user.getProfile() != null ? user.getProfile().getRegion() : null
        ));
    }

    @Transactional
    public ResponseEntity<?> createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User(
                request.username(), 
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role() != null ? request.role() : "ROLE_USER"
        );

        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User created successfully by Admin."));
    }

    @Transactional
    public ResponseEntity<?> updateUser(UUID id, AdminUpdateUserRequest request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }

        User user = userOpt.get();

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken."));
            }
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use."));
            }
            user.setEmail(request.email());
        }

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
        return ResponseEntity.ok(new MessageResponse("User updated successfully by Admin."));
    }

    @Transactional
    public ResponseEntity<?> deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully by Admin."));
    }
}
