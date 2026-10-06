package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.advice.ApiError;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description = "Endpoints for registering, logging in, and managing JWT cookies")
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
  @Operation(
      summary = "Login User",
      description = "Authenticate to get HttpOnly cookies (leo_chat_jwt and leo_chat_jwt_refresh).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
      })
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.UserInfoResponse.class)))
  @PostMapping("/login")
  public ResponseEntity<com.example.chat_app_backend.payload.response.UserInfoResponse> authenticateUser(
      @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
    String deviceInfo = request.getHeader(HEADER_USER_AGENT);
    TokenResponse tokenResponse = authService.login(loginRequest, deviceInfo);

    ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(tokenResponse.accessToken());
    ResponseCookie jwtRefreshCookie =
        jwtUtils.generateRefreshJwtCookie(tokenResponse.refreshToken());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
        .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
        .body(
            new com.example.chat_app_backend.payload.response.UserInfoResponse(
                tokenResponse.id(), tokenResponse.username(), tokenResponse.email()));
  }

  @SecurityRequirements()
  @Operation(summary = "Register User")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "User successfully registered",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Username or Email already taken",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
      })
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.auth.dto.RegisterResponse.class)))
  @PostMapping("/register")
  public ResponseEntity<com.example.chat_app_backend.auth.dto.RegisterResponse> registerUser(
      @Valid @RequestBody RegisterRequest signUpRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(signUpRequest));
  }

  @SecurityRequirements()
  @Operation(
      summary = "Refresh Token",
      description = "Rotate HttpOnly cookies using the refresh cookie.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Tokens successfully rotated", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.UserInfoResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid/expired refresh cookie",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
      })
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.UserInfoResponse.class)))
  @PostMapping("/refresh")
  public ResponseEntity<com.example.chat_app_backend.payload.response.UserInfoResponse> refreshtoken(HttpServletRequest request) {
    String refreshToken = jwtUtils.getJwtRefreshFromCookies(request);
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new com.example.chat_app_backend.exception.UnauthorizedException("Refresh Token is empty!");
    }

    String deviceInfo = request.getHeader(HEADER_USER_AGENT);
    TokenResponse tokenResponse = authService.refresh(refreshToken, deviceInfo);

    ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(tokenResponse.accessToken());
    ResponseCookie jwtRefreshCookie =
        jwtUtils.generateRefreshJwtCookie(tokenResponse.refreshToken());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
        .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
        .body(
            new com.example.chat_app_backend.payload.response.UserInfoResponse(
                tokenResponse.id(), tokenResponse.username(), tokenResponse.email()));
  }

  @Operation(summary = "Logout User", description = "Clear HttpOnly cookies.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully logged out",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid bearer token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
      })
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
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

  @Operation(
      summary = "Logout All Sessions",
      description = "Revoke all sessions and clear HttpOnly cookies.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully logged out of all sessions",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid bearer token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
      })
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/logout-all")
  public ResponseEntity<MessageResponse> logoutAll(
      @AuthenticationPrincipal UserDetailsImpl userDetails) {
    authService.logoutAll(userDetails.getId());
    ResponseCookie jwtCookie = jwtUtils.getCleanJwtCookie();
    ResponseCookie jwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
        .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
        .body(new MessageResponse("Successfully logged out of all sessions"));
  }

  @Operation(
      summary = "Change Password",
      description =
          "Requires current password; revokes other sessions; clears must_change_password")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/change-password")
  public ResponseEntity<MessageResponse> changePassword(
      @Valid @RequestBody com.example.chat_app_backend.auth.dto.ChangePasswordRequest request,
      @AuthenticationPrincipal UserDetailsImpl userDetails) {
    authService.changePassword(userDetails.getId(), request);
    ResponseCookie jwtCookie = jwtUtils.getCleanJwtCookie();
    ResponseCookie jwtRefreshCookie = jwtUtils.getCleanJwtRefreshCookie();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
        .header(HttpHeaders.SET_COOKIE, jwtRefreshCookie.toString())
        .body(new MessageResponse("Password changed successfully, all sessions revoked."));
  }

  @SecurityRequirements()
  @Operation(
      summary = "Forgot Password",
      description = "Email a reset token (always responds 202, no user enumeration)")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/forgot-password")
  public ResponseEntity<MessageResponse> forgotPassword(
      @Valid @RequestBody com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    String token = authService.forgotPassword(request);
    return ResponseEntity.accepted().body(new MessageResponse("DEV MODE TOKEN: " + token));
  }

  @SecurityRequirements()
  @Operation(summary = "Reset Password", description = "Set new password with token")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/reset-password")
  public ResponseEntity<MessageResponse> resetPassword(
      @Valid @RequestBody com.example.chat_app_backend.auth.dto.ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
  }

  @SecurityRequirements()
  @Operation(summary = "Verify Email", description = "Confirm email token")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/verify-email")
  public ResponseEntity<MessageResponse> verifyEmail(
      @Valid @RequestBody com.example.chat_app_backend.auth.dto.VerifyEmailRequest request) {
    authService.verifyEmail(request);
    return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
  }

  @SecurityRequirements()
  @Operation(
      summary = "Resend Verification",
      description = "Resend verification email (rate-limited)")
  @ApiResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  @PostMapping("/resend-verification")
  public ResponseEntity<MessageResponse> resendVerification(
      @Valid @RequestBody com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    String token = authService.resendVerification(request);
    return ResponseEntity.accepted().body(new MessageResponse("DEV MODE TOKEN: " + token));
  }
}
