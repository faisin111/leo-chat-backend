package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.auth.model.RefreshToken;
import com.example.chat_app_backend.auth.model.VerificationToken;
import com.example.chat_app_backend.auth.repo.RefreshTokenRepository;
import com.example.chat_app_backend.auth.repo.VerificationTokenRepository;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final String ADMIN_LOWER = "admin";
  private static final String ROLE_ADMIN_LOWER = "role_admin";
  private static final String ERR_USER_NOT_FOUND = "User not found";
  private static final String ERR_INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
  private static final String ERR_REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
  private static final String ALGO_SHA_256 = "SHA-256";
  private static final String ERR_SHA_NOT_AVAILABLE = "SHA-256 algorithm not available";

  private final UserRepository userRepository;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authenticationManager;
  private final JwtUtils jwtUtils;
  private final RefreshTokenRepository refreshTokenRepository;
  private final VerificationTokenRepository verificationTokenRepository;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtUtils jwtUtils,
      RefreshTokenRepository refreshTokenRepository,
      VerificationTokenRepository verificationTokenRepository) {
    this.userRepository = userRepository;
    this.encoder = encoder;
    this.authenticationManager = authenticationManager;
    this.jwtUtils = jwtUtils;
    this.refreshTokenRepository = refreshTokenRepository;
    this.verificationTokenRepository = verificationTokenRepository;
  }

  @Transactional
  public com.example.chat_app_backend.auth.dto.RegisterResponse registerUser(
      RegisterRequest signUpRequest) {
    if (userRepository.existsByUsername(signUpRequest.username())) {
      throw new ConflictException(AppConstants.ERR_USERNAME_TAKEN);
    }

    if (userRepository.existsByEmail(signUpRequest.email())) {
      throw new ConflictException(AppConstants.ERR_EMAIL_TAKEN);
    }

    String role = AppConstants.ROLE_USER;
    if (!userRepository.existsByRole(AppConstants.ROLE_ADMIN)) {
      role = AppConstants.ROLE_ADMIN;
    }

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

    return new com.example.chat_app_backend.auth.dto.RegisterResponse(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
  }

  @Transactional
  public TokenResponse login(LoginRequest loginRequest, String deviceInfo) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.username(), loginRequest.password()));

    UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
    User user =
        userRepository
            .findById(userDetails.getId())
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    return issueTokens(user, UUID.randomUUID(), deviceInfo);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken, String deviceInfo) {
    String hash = sha256(rawRefreshToken);
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN));

    if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      throw new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN);
    }
    if (token.getUsedAt() != null) {
      refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
      throw new UnauthorizedException(ERR_REFRESH_TOKEN_REUSED);
    }

    token.setUsedAt(Instant.now());
    refreshTokenRepository.save(token);

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    return issueTokens(user, token.getFamilyId(), deviceInfo);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    String hash = sha256(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresent(
            token -> {
              token.setRevokedAt(Instant.now());
              refreshTokenRepository.save(token);
            });
  }

  @Transactional
  public void logoutAll(UUID userId) {
    refreshTokenRepository.revokeAllForUser(userId, Instant.now());
  }

  private TokenResponse issueTokens(User user, UUID familyId, String deviceInfo) {
    String accessToken = jwtUtils.generateTokenFromUsername(user.getUsername());
    String rawRefreshToken = generateRandomToken();
    String hash = sha256(rawRefreshToken);

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setId(UUID.randomUUID());
    refreshToken.setUserId(user.getId());
    refreshToken.setTokenHash(hash);
    refreshToken.setFamilyId(familyId);
    refreshToken.setDeviceInfo(deviceInfo);
    refreshToken.setCreatedAt(Instant.now());
    // assuming 7 days TTL for refresh token
    refreshToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

    refreshTokenRepository.save(refreshToken);

    return new TokenResponse(
        accessToken, rawRefreshToken, user.getId(), user.getUsername(), user.getEmail());
  }

  private String generateRandomToken() {
    return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGO_SHA_256);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(ERR_SHA_NOT_AVAILABLE, e);
    }
  }

  @Transactional
  public void changePassword(
      UUID userId, com.example.chat_app_backend.auth.dto.ChangePasswordRequest request) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
    if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new RuntimeException("Invalid current password");
    }
    user.setPasswordHash(encoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    userRepository.save(user);
    logoutAll(userId);
  }

  @Transactional
  public void forgotPassword(com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            user -> {
              verificationTokenRepository.deleteByUserIdAndType(user.getId(), "PASSWORD_RESET");
              String token = UUID.randomUUID().toString();
              VerificationToken vToken =
                  new VerificationToken(
                      token,
                      user.getId(),
                      "PASSWORD_RESET",
                      Instant.now().plus(java.time.Duration.ofHours(1)));
              verificationTokenRepository.save(vToken);
              // TODO: Send email
              System.out.println("PASSWORD RESET TOKEN FOR " + user.getEmail() + ": " + token);
            });
  }

  @Transactional
  public void resetPassword(com.example.chat_app_backend.auth.dto.ResetPasswordRequest request) {
    VerificationToken vToken =
        verificationTokenRepository
            .findByTokenAndType(request.token(), "PASSWORD_RESET")
            .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

    if (vToken.getExpiresAt().isBefore(Instant.now())) {
      verificationTokenRepository.delete(vToken);
      throw new RuntimeException("Token expired");
    }

    User user =
        userRepository
            .findById(vToken.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

    user.setPasswordHash(encoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    userRepository.save(user);

    verificationTokenRepository.delete(vToken);
    logoutAll(user.getId());
  }

  @Transactional
  public void verifyEmail(com.example.chat_app_backend.auth.dto.VerifyEmailRequest request) {
    VerificationToken vToken =
        verificationTokenRepository
            .findByTokenAndType(request.token(), "EMAIL_VERIFICATION")
            .orElseThrow(() -> new RuntimeException("Invalid or expired verification token"));

    if (vToken.getExpiresAt().isBefore(Instant.now())) {
      verificationTokenRepository.delete(vToken);
      throw new RuntimeException("Token expired");
    }

    // Mark user as verified (if we had a verified field, but for now just delete the token)
    System.out.println("User " + vToken.getUserId() + " successfully verified email.");
    verificationTokenRepository.delete(vToken);
  }

  @Transactional
  public void resendVerification(
      com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            user -> {
              verificationTokenRepository.deleteByUserIdAndType(user.getId(), "EMAIL_VERIFICATION");
              String token = UUID.randomUUID().toString();
              VerificationToken vToken =
                  new VerificationToken(
                      token,
                      user.getId(),
                      "EMAIL_VERIFICATION",
                      Instant.now().plus(java.time.Duration.ofHours(24)));
              verificationTokenRepository.save(vToken);
              // TODO: Send email
              System.out.println("EMAIL VERIFICATION TOKEN FOR " + user.getEmail() + ": " + token);
            });
  }
}
