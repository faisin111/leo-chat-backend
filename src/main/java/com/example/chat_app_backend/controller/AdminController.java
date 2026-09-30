package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.payload.request.AdminCreateUserRequest;
import com.example.chat_app_backend.payload.request.AdminUpdateUserRequest;
import com.example.chat_app_backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.chat_app_backend.config.openapi.StandardErrors;

import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "06 Admin", description = "Highly privileged endpoints strictly reserved for the single System Admin")
@StandardErrors
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Get All Users", description = "Retrieves a directory of every registered user in the database.")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return adminService.getAllUsers();
    }


    @Operation(summary = "Get User by ID", description = "Admin retrieves a specific user's details.")
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        return adminService.getUserById(id);
    }

    @Operation(summary = "Create User", description = "Admin explicitly creates a new user, bypassing registration.")

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return adminService.createUser(request);
    }

    @Operation(summary = "Update User", description = "Admin updates a user's details (username, email, etc).")
    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @Valid @RequestBody AdminUpdateUserRequest request) {
        return adminService.updateUser(id, request);
    }

    @Operation(summary = "Delete User", description = "Permanently deletes a user from the database by their UUID.")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        return adminService.deleteUser(id);
    }
}
