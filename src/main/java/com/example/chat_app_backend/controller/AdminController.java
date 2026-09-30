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


import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Highly privileged endpoints strictly reserved for the single System Admin")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Get Stats Overview")
    @GetMapping("/stats/overview")
    public ResponseEntity<?> getStatsOverview() {
        return adminService.getStatsOverview();
    }

    @Operation(summary = "List and filter users")
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestParam(required = false) String q, 
                                      @RequestParam(required = false) String status, 
                                      @RequestParam(defaultValue = "0") int cursor, 
                                      @RequestParam(defaultValue = "50") int limit) {
        return adminService.getUsers(q, status, cursor, limit);
    }

    @Operation(summary = "Get User by ID")
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        return adminService.getUserById(id);
    }

    @Operation(summary = "Update user status (ACTIVE, DISABLED, BANNED)")
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable UUID id, @RequestBody com.example.chat_app_backend.payload.request.UpdateUserStatusRequest request) {
        return adminService.updateUserStatus(id, request);
    }

    @Operation(summary = "Force logout user")
    @PostMapping("/users/{id}/force-logout")
    public ResponseEntity<?> forceLogout(@PathVariable UUID id) {
        return adminService.forceLogout(id);
    }

    @Operation(summary = "Soft delete and anonymize user")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        return adminService.deleteUser(id);
    }

    @Operation(summary = "Get conversation metadata")
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@RequestParam(required = false) String q, 
                                              @RequestParam(required = false) String status, 
                                              @RequestParam(defaultValue = "0") int cursor) {
        return adminService.getConversations(q, status, cursor);
    }

    @Operation(summary = "Update conversation status")
    @PatchMapping("/conversations/{id}/status")
    public ResponseEntity<?> updateConversationStatus(@PathVariable UUID id, @RequestBody com.example.chat_app_backend.payload.request.UpdateConversationStatusRequest request) {
        return adminService.updateConversationStatus(id, request);
    }

    @Operation(summary = "Moderation queue")
    @GetMapping("/reports")
    public ResponseEntity<?> getReports(@RequestParam(required = false) String status, 
                                        @RequestParam(defaultValue = "0") int cursor) {
        return adminService.getReports(status, cursor);
    }

    @Operation(summary = "Report detail")
    @GetMapping("/reports/{id}")
    public ResponseEntity<?> getReportDetail(@PathVariable UUID id) {
        return adminService.getReportDetail(id);
    }

    @Operation(summary = "Resolve report")
    @PatchMapping("/reports/{id}")
    public ResponseEntity<?> resolveReport(@PathVariable UUID id, @RequestBody com.example.chat_app_backend.payload.request.ResolveReportRequest request) {
        return adminService.resolveReport(id, request);
    }

    @Operation(summary = "Remove a reported message")
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable UUID id, @RequestParam String reason) {
        return adminService.deleteMessage(id, reason);
    }

    @Operation(summary = "Browse audit trail")
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(@RequestParam(required = false) UUID actor, 
                                          @RequestParam(required = false) String action, 
                                          @RequestParam(required = false) String from, 
                                          @RequestParam(required = false) String to, 
                                          @RequestParam(defaultValue = "0") int cursor) {
        return adminService.getAuditLogs(actor, action, from, to, cursor);
    }

    @Operation(summary = "Transfer Admin Ownership")
    @PostMapping("/transfer-ownership")
    public ResponseEntity<?> transferOwnership(@RequestBody com.example.chat_app_backend.payload.request.TransferOwnershipRequest request) {
        return adminService.transferOwnership(request);
    }
}
