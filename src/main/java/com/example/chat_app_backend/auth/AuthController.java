package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.advice.ApiError;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenRefreshRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private static final String HEADER_USER_AGENT = "User-Agent";
    
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @SecurityRequirements()
    @Operation(summary = "Login User", description = "Authenticate with username and password to get a JWT access token and refresh token.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully authenticated", 
                     content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials (UNAUTHORIZED)", 
                     content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "400", description = "Validation error on input", 
                     content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        String deviceInfo = request.getHeader(HEADER_USER_AGENT);
        TokenResponse tokenResponse = authService.login(loginRequest, deviceInfo);
        return ResponseEntity.ok(tokenResponse);
    }

    @SecurityRequirements()
    @Operation(summary = "Register User", description = "Register a new user account.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User successfully registered"),
        @ApiResponse(responseCode = "409", description = "Username or Email already taken (CONFLICT)", 
                     content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "400", description = "Validation error on input (e.g., weak password)", 
                     content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<Void> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        authService.registerUser(signUpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @SecurityRequirements()
    @Operation(summary = "Refresh Token", description = "Exchange a valid refresh token for a new access token and a new refresh token (token rotation).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens successfully rotated", 
                     content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid, expired, or reused refresh token (UNAUTHORIZED)", 
                     content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "400", description = "Validation error (missing token)", 
                     content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshtoken(
            @Valid @RequestBody TokenRefreshRequest requestBody,
            HttpServletRequest request) {
        String deviceInfo = request.getHeader(HEADER_USER_AGENT);
        TokenResponse tokenResponse = authService.refresh(requestBody.refreshToken(), deviceInfo);
        return ResponseEntity.ok(tokenResponse);
    }
    
    @Operation(summary = "Logout User", description = "Revoke the specific refresh token so it cannot be used again.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Successfully logged out"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", 
                     content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@Valid @RequestBody TokenRefreshRequest requestBody) {
        authService.logout(requestBody.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Logout All Sessions", description = "Revoke all refresh tokens for the currently authenticated user across all devices.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Successfully logged out of all sessions"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", 
                     content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        authService.logoutAll(userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
