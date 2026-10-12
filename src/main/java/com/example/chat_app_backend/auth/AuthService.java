package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final com.example.chat_app_backend.service.EmailService emailService;

  private static final String ADMIN_LOWER = "admin";
  private static final String ROLE_ADMIN_LOWER = "role_admin";
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
      com.example.chat_app_backend.service.EmailService emailService) {
    this.userRepository = userRepository;
    this.encoder = encoder;
    this.authenticationManager = authenticationManager;
    this.jwtUtils = jwtUtils;
    this.emailService = emailService;
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

    // Generate and send verification email automatically on registration
    String token = jwtUtils.generateEmailVerificationToken(user.getEmail());
    emailService.sendVerificationEmail(user.getEmail(), token);

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

    return issueTokens(user);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken, String deviceInfo) {
    if (!jwtUtils.validateRefreshToken(rawRefreshToken)) {
      throw new UnauthorizedException(ERR_INVALID_REFRESH_TOKEN);
    }

    String username = jwtUtils.getUserNameFromJwtToken(rawRefreshToken);
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

    if ("DISABLED".equals(user.getStatus())) {
      throw new UnauthorizedException("User account is disabled");
    }

    return issueTokens(user);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    // Pure stateless: tokens are not stored in database. Client clears cookies.
  }

  @Transactional
  public void logoutAll(UUID userId) {
    // Pure stateless: tokens are not stored in database. Client clears cookies.
  }

  private TokenResponse issueTokens(User user) {
    String accessToken = jwtUtils.generateTokenFromUsername(user.getUsername());
    String rawRefreshToken = jwtUtils.generateRefreshToken(user.getUsername());

    return new TokenResponse(
        accessToken, rawRefreshToken, user.getId(), user.getUsername(), user.getEmail());
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
  public String forgotPassword(com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    return userRepository
        .findByEmail(request.email())
        .map(
            user -> {
              String token = jwtUtils.generatePasswordResetToken(user.getEmail(), user.getPasswordHash());
              emailService.sendPasswordResetEmail(user.getEmail(), token);
              return token;
            })
        .orElse(null);
  }

  @Transactional
  public void resetPassword(com.example.chat_app_backend.auth.dto.ResetPasswordRequest request) {
    String email;
    try {
      email = jwtUtils.getEmailFromPasswordResetToken(request.token());
    } catch (Exception e) {
      throw new RuntimeException("Invalid or expired reset token");
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    if (!jwtUtils.validatePasswordResetToken(request.token(), user.getPasswordHash())) {
      throw new RuntimeException("Invalid or expired reset token");
    }

    user.setPasswordHash(encoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    userRepository.save(user);

    logoutAll(user.getId());
  }

  @Transactional
  public void verifyEmail(com.example.chat_app_backend.auth.dto.VerifyEmailRequest request) {
    if (!jwtUtils.validateEmailVerificationToken(request.token())) {
      throw new RuntimeException("Invalid or expired verification token");
    }

    String email;
    try {
      email = jwtUtils.getEmailFromVerificationToken(request.token());
    } catch (Exception e) {
      throw new RuntimeException("Invalid or expired verification token");
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    user.setEmailVerified(true);
    userRepository.save(user);

    System.out.println("User " + user.getEmail() + " successfully verified email.");
  }

  @Transactional
  public String resendVerification(
      com.example.chat_app_backend.auth.dto.ForgotPasswordRequest request) {
    return userRepository
        .findByEmail(request.email())
        .map(
            user -> {
              String token = jwtUtils.generateEmailVerificationToken(user.getEmail());
              emailService.sendVerificationEmail(user.getEmail(), token);
              return token;
            })
        .orElse(null);
  }
}

