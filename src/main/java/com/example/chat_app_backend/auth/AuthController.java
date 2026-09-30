package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenRefreshRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for registering, logging in, and managing JWT tokens")
public class AuthController {
    
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @SecurityRequirements()
    @Operation(summary = "Login User")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        // Simple user-agent or IP as device info
        String deviceInfo = request.getHeader("User-Agent");
        TokenResponse tokenResponse = authService.login(loginRequest, deviceInfo);
        return ResponseEntity.ok(tokenResponse);
    }

    @SecurityRequirements()
    @Operation(summary = "Register User")
    @PostMapping("/register")
    public ResponseEntity<Void> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        authService.registerUser(signUpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @SecurityRequirements()
    @Operation(summary = "Refresh Token")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshtoken(
            @Valid @RequestBody TokenRefreshRequest requestBody,
            HttpServletRequest request) {
        String deviceInfo = request.getHeader("User-Agent");
        TokenResponse tokenResponse = authService.refresh(requestBody.refreshToken(), deviceInfo);
        return ResponseEntity.ok(tokenResponse);
    }
    
    @Operation(summary = "Logout User")
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@Valid @RequestBody TokenRefreshRequest requestBody) {
        authService.logout(requestBody.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Logout All Sessions")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
