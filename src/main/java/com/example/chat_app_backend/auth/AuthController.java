package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.advice.ApiError;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import com.example.chat_app_backend.payload.response.MessageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for registering, logging in, and managing JWT cookies")
@StandardErrors
public class AuthController {
    
    private static final String HEADER_USER_AGENT = "User-Agent";
    private final AuthService authService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthService authService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
    }

    @SecurityRequirements()
    @Operation(summary = "Login User", description = "Authenticate to get HttpOnly cookies (leo_chat_jwt and leo_chat_jwt_refresh).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        String deviceInfo = request.getHeader(HEADER_USER_AGENT);
        TokenResponse tokenResponse = authService.login(loginRequest, deviceInfo);
        
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(tokenResponse.accessToken());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(tokenResponse.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(tokenResponse);
    }

    @SecurityRequirements()
    @Operation(summary = "Register User")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User successfully registered", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username or Email already taken", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        authService.registerUser(signUpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("User successfully registered"));
    }

    @SecurityRequirements()
    @Operation(summary = "Refresh Token", description = "Rotate HttpOnly cookies using the refresh cookie.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens successfully rotated"),
        @ApiResponse(responseCode = "401", description = "Invalid/expired refresh cookie", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshtoken(HttpServletRequest request) {
        String refreshToken = jwtUtils.getJwtRefreshFromCookies(request);
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(java.time.Instant.now(), 401, "UNAUTHORIZED", "Refresh Token is empty!", "/api/v1/auth/refresh", java.util.UUID.randomUUID().toString(), java.util.List.of()));
        }

        String deviceInfo = request.getHeader(HEADER_USER_AGENT);
        TokenResponse tokenResponse = authService.refresh(refreshToken, deviceInfo);
        
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(tokenResponse.accessToken());
        ResponseCookie jwtRefreshCookie = jwtUtils.generateRefreshJwtCookie(tokenResponse.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(tokenResponse);
    }
    
    @Operation(summary = "Logout User", description = "Clear HttpOnly cookies.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully logged out", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logoutUser(HttpServletRequest request) {
        String refreshToken = jwtUtils.getJwtRefreshFromCookies(request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        ResponseCookie jwtCookie = jwtUtils.getCleanJwtCookie();
        ResponseCookie jwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(new MessageResponse("Successfully logged out"));
    }

    @Operation(summary = "Logout All Sessions", description = "Revoke all sessions and clear HttpOnly cookies.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully logged out of all sessions", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAll(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        authService.logoutAll(userDetails.getId());
        ResponseCookie jwtCookie = jwtUtils.getCleanJwtCookie();
        ResponseCookie jwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(new MessageResponse("Successfully logged out of all sessions"));
    }

    @Operation(summary = "Change Password", description = "Requires current password; revokes other sessions; clears must_change_password")
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody com.example.chat_app_backend.auth.dto.ChangePasswordRequest request, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        authService.changePassword(userDetails.getId(), request);
        ResponseCookie jwtCookie = jwtUtils.getCleanJwtCookie();
        ResponseCookie jwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
                .body(new MessageResponse("Password changed successfully, all sessions revoked."));
    }

    @SecurityRequirements()
    @Operation(summary = "Forgot Password", description = "Email a reset token (always responds 202, no user enumeration)")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @SecurityRequirements()
    @Operation(summary = "Reset Password", description = "Set new password with token")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody com.example.chat_app_backend.auth.dto.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }

    @SecurityRequirements()
    @Operation(summary = "Verify Email", description = "Confirm email token")
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody com.example.chat_app_backend.auth.dto.VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @SecurityRequirements()
    @Operation(summary = "Resend Verification", description = "Resend verification email (rate-limited)")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.accepted().build();
    }
}
