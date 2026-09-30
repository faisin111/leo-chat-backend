package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "Highly privileged endpoints strictly reserved for the single System Admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Get All Users", description = "Retrieves a massive directory of every registered user in the database.")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return adminService.getAllUsers();
    }

    @Operation(summary = "Delete User", description = "Permanently deletes a user from the database by their UUID.")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        return adminService.deleteUser(id);
    }

    @Operation(summary = "Get All Chats", description = "Retrieves all chat rooms (both direct messages and groups) across the entire platform.")
    @GetMapping("/chats")
    public ResponseEntity<?> getAllChats() {
        return adminService.getAllChats();
    }
}
