package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;


@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Admin",
    description = "Highly privileged endpoints strictly reserved for the single System Admin")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class AdminController {

  @Autowired private AdminService adminService;

  @Operation(summary = "Get Stats Overview")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/stats/overview")
  public ResponseEntity<java.util.Map<String, Object>> getStatsOverview() {
    return adminService.getStatsOverview();
  }

  @Operation(summary = "List and filter users")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/users")
  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<com.example.chat_app_backend.model.User>> getUsers(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return adminService.getUsers(q, status, cursor, limit);
  }

  @Operation(summary = "Get User by ID")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/users/{id}")
  public ResponseEntity<java.util.Map<String, Object>> getUserById(@PathVariable UUID id) {
    return adminService.getUserById(id);
  }

  @Operation(summary = "Update user status (ACTIVE, DISABLED, BANNED)")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PatchMapping("/users/{id}/status")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> updateUserStatus(
      @PathVariable UUID id,
      @RequestBody com.example.chat_app_backend.payload.request.UpdateUserStatusRequest request) {
    return adminService.updateUserStatus(id, request);
  }

  @Operation(summary = "Force logout user")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/users/{id}/force-logout")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> forceLogout(@PathVariable UUID id) {
    return adminService.forceLogout(id);
  }

  @Operation(summary = "Soft delete and anonymize user")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @DeleteMapping("/users/{id}")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> deleteUser(@PathVariable UUID id) {
    return adminService.deleteUser(id);
  }

  @Operation(summary = "Get conversation metadata")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/conversations")
  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<com.example.chat_app_backend.chat.conversation.Conversation>> getConversations(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int cursor) {
    return adminService.getConversations(q, status, cursor);
  }

  @Operation(summary = "Update conversation status")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PatchMapping("/conversations/{id}/status")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> updateConversationStatus(
      @PathVariable UUID id,
      @RequestBody
          com.example.chat_app_backend.payload.request.UpdateConversationStatusRequest request) {
    return adminService.updateConversationStatus(id, request);
  }

  @Operation(summary = "Moderation queue")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/reports")
  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<com.example.chat_app_backend.model.Report>> getReports(
      @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int cursor) {
    return adminService.getReports(status, cursor);
  }

  @Operation(summary = "Report detail")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/reports/{id}")
  public ResponseEntity<com.example.chat_app_backend.model.Report> getReportDetail(@PathVariable UUID id) {
    return adminService.getReportDetail(id);
  }

  @Operation(summary = "Resolve report")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PatchMapping("/reports/{id}")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> resolveReport(
      @PathVariable UUID id,
      @RequestBody com.example.chat_app_backend.payload.request.ResolveReportRequest request) {
    return adminService.resolveReport(id, request);
  }

  @Operation(summary = "Remove a reported message")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @DeleteMapping("/messages/{id}")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> deleteMessage(@PathVariable UUID id, @RequestParam String reason) {
    return adminService.deleteMessage(id, reason);
  }

  @Operation(summary = "Browse audit trail")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @GetMapping("/audit-logs")
  public ResponseEntity<com.example.chat_app_backend.payload.response.CursorPageResponse<com.example.chat_app_backend.model.AuditLog>> getAuditLogs(
      @RequestParam(required = false) UUID actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int cursor) {
    return adminService.getAuditLogs(actor, action, from, to, cursor);
  }

  @Operation(summary = "Transfer Admin Ownership")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/transfer-ownership")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> transferOwnership(
      @RequestBody com.example.chat_app_backend.payload.request.TransferOwnershipRequest request) {
    return adminService.transferOwnership(request);
  }
}
