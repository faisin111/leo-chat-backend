package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.ChangePasswordRequest;
import com.example.chat_app_backend.auth.dto.ForgotPasswordRequest;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.RegisterResponse;
import com.example.chat_app_backend.auth.dto.ResetPasswordRequest;
import com.example.chat_app_backend.auth.dto.TokenRefreshRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.auth.dto.VerifyEmailRequest;
import com.example.chat_app_backend.config.openapi.StandardErrors;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.payload.response.MessageResponse;
import com.example.chat_app_backend.security.jwt.AuthCookies;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description =
        "JWT authentication with database-backed refresh sessions, browser cookies and account recovery")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "cookieAuth")
@StandardErrors
public class AuthController {
  private static final String RECOVERY_MESSAGE =
      "If the account is eligible, an email will be sent.";
  private final AuthCookies cookies;
  private final AuthService authService;

  public AuthController(AuthService authService, AuthCookies cookies) {
    this.cookies = cookies;
    this.authService = authService;
  }

  @SecurityRequirements
  @Operation(
      summary = "Get browser CSRF token",
      description =
          "Initialize XSRF-TOKEN cookie; send returned token in X-XSRF-TOKEN for browser writes.",
      operationId = "auth_csrf")
  @ApiResponse(responseCode = "200", description = "CSRF token and header name")
  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
  }

  @SecurityRequirements
  @Operation(
      summary = "Log in",
      description =
        "Set HttpOnly access/refresh cookies, create a refresh session, and return the JSON token pair for API compatibility.",
      operationId = "auth_login")
  @ApiResponse(
      responseCode = "200",
      description = "Bearer tokens and expiry metadata",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return tokens(
        authService.login(
            request,
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent"),
            servletRequest.getHeader("X-Device-Name"),
            servletRequest.getHeader("X-Device-Platform"),
            servletRequest.getHeader("X-App-Version")));
  }

  @SecurityRequirements
  @Operation(
      summary = "Register account",
      description =
          "Create an account, create a refresh session, set auth cookies, and return tokens for immediate app access. Email verification instructions are also sent.",
      operationId = "auth_register")
  @ApiResponse(
      responseCode = "201",
      description = "Account created",
      content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
  @ApiResponse(responseCode = "409", description = "Username or email already exists")
  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    RegisterResponse response =
        authService.registerUser(
            request,
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent"),
            servletRequest.getHeader("X-Device-Name"),
            servletRequest.getHeader("X-Device-Platform"),
            servletRequest.getHeader("X-App-Version"));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .header(
            "Set-Cookie",
            cookies.access(response.accessToken(), response.expiresIn()).toString(),
            cookies.refresh(response.refreshToken(), response.refreshExpiresIn()).toString(),
            cookies.clearLegacyAccess().toString())
        .body(response);
  }

  @SecurityRequirements
  @Operation(
      summary = "Refresh tokens",
      description =
        "Read refresh JWT from the stored HttpOnly session cookie, with JSON fallback for API clients. Refresh rotates the stored token while preserving the session.",
      operationId = "auth_refresh")
  @ApiResponse(
      responseCode = "200",
      description = "Bearer tokens and remaining expiry",
      content = @Content(schema = @Schema(implementation = TokenResponse.class)))
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @Valid @RequestBody(required = false) TokenRefreshRequest request,
      HttpServletRequest servletRequest) {
    String refreshToken = cookies.refreshToken(servletRequest);
    if (refreshToken == null && request != null) refreshToken = request.refreshToken();
    if (refreshToken == null || refreshToken.isBlank())
      throw new UnauthorizedException("Refresh token is required");
    return tokens(
        authService.refresh(
            refreshToken,
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent"),
            servletRequest.getHeader("X-Device-Name"),
            servletRequest.getHeader("X-Device-Platform"),
            servletRequest.getHeader("X-App-Version")));
  }

  @SecurityRequirements
  @Operation(
      summary = "Clear browser authentication cookies",
      description = "Revoke the current refresh session and clear browser authentication cookies. Existing access JWTs remain valid until expiry.",
      operationId = "auth_logout")
  @ApiResponse(
      responseCode = "204",
      description = "Client logout acknowledged; no response body",
      content = @Content)
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
    authService.logout(cookies.refreshToken(servletRequest));
    return ResponseEntity.noContent()
        .header(
            "Set-Cookie",
            cookies.clearAccess().toString(),
            cookies.clearRefresh().toString(),
            cookies.clearLegacyAccess().toString())
        .build();
  }

  @Operation(
      summary = "Reject server logout",
      description =
        "Revoke every stored refresh session for the authenticated user.",
      operationId = "auth_logout_all")
  @ApiResponse(responseCode = "422", description = "Server-side revocation is unsupported")
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserDetailsImpl user) {
    authService.logoutAll(user.getId());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Change password",
      description =
          "Require current password and update credentials. Previously issued JWTs remain valid until expiry.",
      operationId = "auth_change_password")
  @ApiResponse(responseCode = "200", description = "Password changed")
  @PostMapping("/change-password")
  public ResponseEntity<MessageResponse> changePassword(
      @AuthenticationPrincipal UserDetailsImpl user,
      @Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(user.getId(), request);
    return ResponseEntity.ok(
        new MessageResponse(
            "Password changed. Delete your locally stored tokens and sign in again."));
  }

  @SecurityRequirements
  @Operation(
      summary = "Request password reset",
      description =
          "Return the same response for known and unknown emails. Recovery tokens are sent only by email.",
      operationId = "auth_forgot_password")
  @ApiResponse(responseCode = "202", description = "Recovery request accepted")
  @PostMapping("/forgot-password")
  public ResponseEntity<MessageResponse> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.accepted().body(new MessageResponse(RECOVERY_MESSAGE));
  }

  @SecurityRequirements
  @Operation(
      summary = "Reset password",
      description =
          "Validate emailed recovery token and update credentials. Existing access/refresh JWTs remain valid until expiry.",
      operationId = "auth_reset_password")
  @ApiResponse(responseCode = "200", description = "Password reset")
  @PostMapping("/reset-password")
  public ResponseEntity<MessageResponse> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
  }

  @SecurityRequirements
  @Operation(
      summary = "Verify email",
      description = "Verify ownership with the emailed verification token.",
      operationId = "auth_verify_email")
  @ApiResponse(responseCode = "200", description = "Email verified")
  @PostMapping("/verify-email")
  public ResponseEntity<MessageResponse> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request);
    return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
  }

  @SecurityRequirements
  @Operation(
      summary = "Resend verification",
      description =
          "Send verification instructions without returning the token or revealing account existence.",
      operationId = "auth_resend_verification")
  @ApiResponse(responseCode = "202", description = "Verification request accepted")
  @PostMapping("/resend-verification")
  public ResponseEntity<MessageResponse> resendVerification(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.resendVerification(request);
    return ResponseEntity.accepted().body(new MessageResponse(RECOVERY_MESSAGE));
  }

  private ResponseEntity<TokenResponse> tokens(TokenResponse response) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Pragma", "no-cache")
        .header(
            "Set-Cookie",
            cookies.access(response.accessToken(), response.expiresIn()).toString(),
            cookies.refresh(response.refreshToken(), response.refreshExpiresIn()).toString(),
            cookies.clearLegacyAccess().toString())
        .body(response);
  }
}
