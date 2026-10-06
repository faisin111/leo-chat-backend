package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints for retrieving user profiles and directories")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class UserController {

  @Autowired private UserService userService;

  @Operation(
      summary = "Get Current User",
      description = "Retrieves the profile of the currently authenticated user.")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.UserProfileResponse.class)))
  @GetMapping("/me")
  public ResponseEntity<com.example.chat_app_backend.payload.response.UserProfileResponse> getCurrentUser() {
    return userService.getCurrentUser();
  }

  @Operation(
      summary = "Get User by ID",
      description = "Retrieves the public profile of a specific user by their UUID.")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.UserProfileResponse.class)))
  @GetMapping("/{id}")
  public ResponseEntity<com.example.chat_app_backend.payload.response.UserProfileResponse> getUserById(@PathVariable UUID id) {
    return userService.getUserById(id);
  }

  @Operation(
      summary = "Update Profile",
      description = "Allows the current user to update their phone number, bio, age, and region.")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.MessageResponse.class)))
  @PatchMapping("/me/profile")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> updateProfile(
      @RequestBody com.example.chat_app_backend.payload.request.UpdateProfileRequest request) {
    return userService.updateProfile(request);
  }

  @Operation(
      summary = "Deactivate Account",
      description = "Deactivate own account (rejected for the admin)")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.MessageResponse.class)))
  @DeleteMapping("/me")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> deactivateAccount() {
    return userService.deactivateCurrentUser();
  }

  @Operation(summary = "Search users")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = java.util.List.class)))
  @GetMapping("/search")
  public ResponseEntity<java.util.List<com.example.chat_app_backend.payload.response.UserSearchResponse>> searchUsers(
      @RequestParam String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return userService.searchUsers(q, page, size);
  }

  @Operation(summary = "Get user presence")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = java.util.List.class)))
  @GetMapping("/presence")
  public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getPresence(@RequestParam java.util.List<UUID> ids) {
    return userService.getPresence(ids);
  }

  @Operation(summary = "Get active sessions")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = java.util.List.class)))
  @GetMapping("/me/sessions")
  public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getSessions(
      @AuthenticationPrincipal
          com.example.chat_app_backend.security.services.UserDetailsImpl userDetails) {
    return userService.getSessions(userDetails.getId());
  }

  @Operation(summary = "Logout specific session")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.MessageResponse.class)))
  @DeleteMapping("/me/sessions/{sessionId}")
  public ResponseEntity<com.example.chat_app_backend.payload.response.MessageResponse> logoutSession(
      @AuthenticationPrincipal
          com.example.chat_app_backend.security.services.UserDetailsImpl userDetails,
      @PathVariable UUID sessionId) {
    return userService.logoutSession(userDetails.getId(), sessionId);
  }
}
