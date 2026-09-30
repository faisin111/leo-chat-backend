package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.chat_app_backend.config.openapi.StandardErrors;

import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/users")
@Tag(name = "User Operations", description = "Endpoints for retrieving user profiles and directories")
@StandardErrors
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "Get Current User", description = "Retrieves the profile of the currently authenticated user.")
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        return userService.getCurrentUser();
    }

    @Operation(summary = "Get User by ID", description = "Retrieves the public profile of a specific user by their UUID.")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @Operation(summary = "Update Profile", description = "Allows the current user to update their phone number, bio, age, and region.")
    @PatchMapping("/me/profile")
    public ResponseEntity<?> updateProfile(@RequestBody com.example.chat_app_backend.payload.request.UpdateProfileRequest request) {
        return userService.updateProfile(request);
    }
}
