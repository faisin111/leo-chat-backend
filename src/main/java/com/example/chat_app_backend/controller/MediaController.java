package com.example.chat_app_backend.controller;

import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.payload.request.MediaConfirmRequest;
import com.example.chat_app_backend.payload.request.MediaPresignRequest;
import com.example.chat_app_backend.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;


@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Endpoints for managing media uploads")
@SecurityRequirement(name = "bearerAuth")
@StandardErrors
public class MediaController {

  @Autowired private MediaService mediaService;

  @Operation(summary = "Get pre-signed upload URL")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/presign")
  public ResponseEntity<java.util.Map<String, Object>> presign(
      @AuthenticationPrincipal
          com.example.chat_app_backend.security.services.UserDetailsImpl userDetails,
      @Valid @RequestBody MediaPresignRequest request) {
    return mediaService.presign(userDetails.getId(), request);
  }

  @Operation(summary = "Confirm upload")
  @ApiResponse(responseCode = "200", description = "Successful response")
  @PostMapping("/confirm")
  public ResponseEntity<java.util.Map<String, Object>> confirm(
      @AuthenticationPrincipal
          com.example.chat_app_backend.security.services.UserDetailsImpl userDetails,
      @Valid @RequestBody MediaConfirmRequest request) {
    return mediaService.confirm(userDetails.getId(), request);
  }
}
