package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.ChangePasswordRequest;
import com.example.chat_app_backend.auth.dto.ForgotPasswordRequest;
import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.RegisterResponse;
import com.example.chat_app_backend.auth.dto.ResetPasswordRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.auth.dto.VerifyEmailRequest;
import com.example.chat_app_backend.common.constants.AppConstants;
import com.example.chat_app_backend.common.enums.UserStatus;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.model.RefreshSession;
import com.example.chat_app_backend.repository.RefreshSessionRepository;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.service.EmailService;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jakarta.servlet.http.HttpServletRequest;
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
  private final RefreshSessionRepository sessionRepository;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtUtils jwtUtils,
      EmailService emailService,
      RefreshSessionRepository sessionRepository) {
    this.userRepository = userRepository;
    this.encoder = encoder;
    this.authenticationManager = authenticationManager;
    this.jwtUtils = jwtUtils;
    this.emailService = emailService;
    this.sessionRepository = sessionRepository;
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
    return login(loginRequest, null, null);
  }

  @Transactional
  public TokenResponse login(LoginRequest loginRequest, String ipAddress, String userAgent) {
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
    return issueTokens(user, null, ipAddress, userAgent);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken) {
    return refresh(rawRefreshToken, null, null);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
    if (!jwtUtils.validateRefreshToken(rawRefreshToken)) {
      throw new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN);
    }

    RefreshSession session;
    if (sessionRepository == null) {
      // Keeps isolated legacy unit tests/API clients compatible during migration.
      session = new RefreshSession();
      session.setExpiresAt(jwtUtils.refreshExpiresAt(rawRefreshToken));
    } else {
      session =
          sessionRepository
              .findByTokenHash(hash(rawRefreshToken))
              .orElseThrow(() -> new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN));
      if (session.getLogoutAt() != null || session.getExpiresAt().isBefore(Instant.now())) {
        throw new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN);
      }
    }

    String username = jwtUtils.getUserNameFromJwtToken(rawRefreshToken);
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    requireActive(user);
    return issueTokens(user, session, ipAddress, userAgent);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
    sessionRepository
        .findByTokenHash(hash(rawRefreshToken))
        .ifPresent(
            session -> {
              if (session.getLogoutAt() == null) session.setLogoutAt(Instant.now());
              sessionRepository.save(session);
            });
  }

  @Transactional
  public void logoutAll(UUID userId) {
    Instant now = Instant.now();
    sessionRepository
        .findByUserIdAndLogoutAtIsNull(userId)
        .forEach(
            session -> {
              session.setLogoutAt(now);
              sessionRepository.save(session);
            });
  }

  private void requireActive(User user) {
    if (!UserStatus.ACTIVE.name().equals(user.getStatus()))
      throw new UnauthorizedException("User account is not active");
  }

  private TokenResponse issueTokens(
      User user, RefreshSession existing, String ipAddress, String userAgent) {
    String accessToken = jwtUtils.generateTokenFromUsername(user.getUsername());
    Instant refreshExpiresAt = existing == null ? null : existing.getExpiresAt();
    String refreshToken =
        refreshExpiresAt == null
            ? jwtUtils.generateRefreshToken(user.getUsername())
            : jwtUtils.generateRefreshToken(user.getUsername(), refreshExpiresAt);
    long remainingRefreshSeconds =
        refreshExpiresAt == null
            ? jwtUtils.refreshExpiresInSeconds()
            : Math.max(0, Duration.between(Instant.now(), refreshExpiresAt).getSeconds());
    if (existing == null) {
      RefreshSession session = new RefreshSession();
      session.setUserId(user.getId());
      session.setCreatedAt(Instant.now());
      session.setUserAgent(userAgent);
      session.setIpAddress(ipAddress);
      existing = session;
    }
    existing.setTokenHash(hash(refreshToken));
    existing.setLastUsedAt(Instant.now());
    existing.setExpiresAt(
        refreshExpiresAt == null
            ? Instant.now().plusSeconds(jwtUtils.refreshExpiresInSeconds())
            : refreshExpiresAt);
    existing.setLogoutAt(null);
    if (sessionRepository != null) sessionRepository.save(existing);
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

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte b : digest) result.append(String.format("%02x", b));
      return result.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
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
