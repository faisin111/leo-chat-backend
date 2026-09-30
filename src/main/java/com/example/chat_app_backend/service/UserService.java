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
                user.getId(), 
                user.getUsername(), 
                user.getEmail(), 
                user.getRole()
        );

        return ResponseEntity.ok(profile);
    }

    public ResponseEntity<?> getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return getUserById(userDetails.getId());
    }

}
