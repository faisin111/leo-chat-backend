package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.ChangePasswordRequest;
import com.example.chat_app_backend.auth.dto.ForgotPasswordRequest;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.RegisterResponse;
import com.example.chat_app_backend.auth.dto.ResetPasswordRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.auth.dto.VerifyEmailRequest;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.service.EmailService;
import com.example.chat_app_backend.util.AppConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final EmailService emailService;

  private static final String ERR_USER_NOT_FOUND = "User not found";
  private static final String ERR_INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

  private final UserRepository userRepository;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authenticationManager;
  private final JwtUtils jwtUtils;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtUtils jwtUtils,
      EmailService emailService) {
    this.userRepository = userRepository;
    this.encoder = encoder;
    this.authenticationManager = authenticationManager;
    this.jwtUtils = jwtUtils;
    this.emailService = emailService;
  }

  @Transactional
  public RegisterResponse registerUser(RegisterRequest signUpRequest) {
    if (userRepository.existsByUsername(signUpRequest.username())) {
      throw new ConflictException(AppConstants.ERR_USERNAME_TAKEN);
    }

    if (userRepository.existsByEmail(signUpRequest.email())) {
      throw new ConflictException(AppConstants.ERR_EMAIL_TAKEN);
    }

    String role = AppConstants.ROLE_USER;

    User user =
        new User(
            signUpRequest.username(),
            signUpRequest.email(),
            encoder.encode(signUpRequest.password()),
            role,
            signUpRequest.displayName() != null
                ? signUpRequest.displayName()
                : signUpRequest.username());

    user = userRepository.save(user);

    // Generate and send verification email automatically on registration
    String token = jwtUtils.generateEmailVerificationToken(user.getEmail());
    emailService.sendVerificationEmail(user.getEmail(), token);

    return new RegisterResponse(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
  }

  @Transactional
  public TokenResponse login(LoginRequest loginRequest) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.username(), loginRequest.password()));

    UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
    User user =
        userRepository
            .findById(userDetails.getId())
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    requireActive(user);
    return issueTokens(user, null);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken) {
    if (!jwtUtils.validateRefreshToken(rawRefreshToken)) {
      throw new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN);
    }

    String username = jwtUtils.getUserNameFromJwtToken(rawRefreshToken);
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    requireActive(user);
    return issueTokens(user, jwtUtils.refreshExpiresAt(rawRefreshToken));
  }

  /** Fully stateless tokens cannot be revoked at the server. */
  public void logoutAll(UUID userId) {
    throw new BusinessRuleException("Server-side logout is unsupported for fully stateless tokens");
  }

  private void requireActive(User user) {
    if (!"ACTIVE".equals(user.getStatus()))
      throw new UnauthorizedException("User account is not active");
  }

  private TokenResponse issueTokens(User user, Instant refreshExpiresAt) {
    String accessToken = jwtUtils.generateTokenFromUsername(user.getUsername());
    String refreshToken =
        refreshExpiresAt == null
            ? jwtUtils.generateRefreshToken(user.getUsername())
            : jwtUtils.generateRefreshToken(user.getUsername(), refreshExpiresAt);
    long remainingRefreshSeconds =
        refreshExpiresAt == null
            ? jwtUtils.refreshExpiresInSeconds()
            : Math.max(0, Duration.between(Instant.now(), refreshExpiresAt).getSeconds());
    return new TokenResponse(
        accessToken,
        "Bearer",
        refreshToken,
        jwtUtils.accessExpiresInSeconds(),
        remainingRefreshSeconds,
        user.getId(),
        user.getUsername(),
        user.getEmail());
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));
    if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new UnauthorizedException("Invalid current password");
    }
    user.setPasswordHash(encoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public void forgotPassword(ForgotPasswordRequest request) {
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            user -> {
              String token =
                  jwtUtils.generatePasswordResetToken(user.getEmail(), user.getPasswordHash());
              emailService.sendPasswordResetEmail(user.getEmail(), token);
            });
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String email;
    try {
      email = jwtUtils.getEmailFromPasswordResetToken(request.token());
    } catch (Exception e) {
      throw new UnauthorizedException("Invalid or expired reset token");
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    if (!jwtUtils.validatePasswordResetToken(request.token(), user.getPasswordHash())) {
      throw new UnauthorizedException("Invalid or expired reset token");
    }

    user.setPasswordHash(encoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    userRepository.save(user);
  }

  @Transactional
  public void verifyEmail(VerifyEmailRequest request) {
    if (!jwtUtils.validateEmailVerificationToken(request.token())) {
      throw new UnauthorizedException("Invalid or expired verification token");
    }

    String email;
    try {
      email = jwtUtils.getEmailFromVerificationToken(request.token());
    } catch (Exception e) {
      throw new UnauthorizedException("Invalid or expired verification token");
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    user.setEmailVerified(true);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public void resendVerification(ForgotPasswordRequest request) {
    userRepository
        .findByEmail(request.email())
        .filter(user -> !user.isEmailVerified())
        .ifPresent(
            user -> {
              String token = jwtUtils.generateEmailVerificationToken(user.getEmail());
              emailService.sendVerificationEmail(user.getEmail(), token);
            });
  }
}
