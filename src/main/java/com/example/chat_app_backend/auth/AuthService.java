package com.example.chat_app_backend.auth;

import com.example.chat_app_backend.auth.dto.LoginRequest;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.TokenResponse;
import com.example.chat_app_backend.auth.model.RefreshToken;
import com.example.chat_app_backend.auth.repo.RefreshTokenRepository;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.exception.UnauthorizedException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.services.UserDetailsImpl;
import com.example.chat_app_backend.util.AppConstants;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.HexFormat;

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

    public AuthService(UserRepository userRepository, 
                       PasswordEncoder encoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void registerUser(RegisterRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.username())) {
            throw new ConflictException(AppConstants.ERR_USERNAME_TAKEN);
        }

        if (userRepository.existsByEmail(signUpRequest.email())) {
            throw new ConflictException(AppConstants.ERR_EMAIL_TAKEN);
        }

        String strRole = signUpRequest.role();
        String role = AppConstants.ROLE_USER;

        if (strRole != null) {
            String lowerRole = strRole.toLowerCase();
            if (lowerRole.equals(ADMIN_LOWER) || lowerRole.equals(ROLE_ADMIN_LOWER)) {
                if (userRepository.existsByRole(AppConstants.ROLE_ADMIN)) {
                    throw new ConflictException(AppConstants.ERR_ADMIN_EXISTS);
                }
                role = AppConstants.ROLE_ADMIN;
            }
        }

        User user = new User(signUpRequest.username(),
                signUpRequest.email(),
                encoder.encode(signUpRequest.password()),
                role);

        userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest loginRequest, String deviceInfo) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

        return issueTokens(user, UUID.randomUUID(), deviceInfo);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String deviceInfo) {
        String hash = sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
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

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException(ERR_USER_NOT_FOUND));

        return issueTokens(user, token.getFamilyId(), deviceInfo);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
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
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
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
}
