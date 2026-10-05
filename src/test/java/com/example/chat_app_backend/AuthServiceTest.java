package com.example.chat_app_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.chat_app_backend.auth.AuthService;
import com.example.chat_app_backend.auth.dto.RegisterRequest;
import com.example.chat_app_backend.auth.dto.RegisterResponse;
import com.example.chat_app_backend.exception.ConflictException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.util.AppConstants;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private AuthService authService;

  @Test
  void registerUser_existingUsername_throwsConflictException() {
    // Arrange
    RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "pass", "Alice");
    when(userRepository.existsByUsername("alice")).thenReturn(true);

    // Act & Assert
    assertThrows(ConflictException.class, () -> authService.registerUser(request));
    verify(userRepository, never()).save(any());
  }

  @Test
  void registerUser_firstUser_becomesAdmin() {
    // Arrange
    RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "pass", "Alice");
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(userRepository.existsByRole(AppConstants.ROLE_ADMIN)).thenReturn(false);
    when(passwordEncoder.encode("pass")).thenReturn("encoded");

    User savedUser =
        new User("alice", "alice@example.com", "encoded", AppConstants.ROLE_ADMIN, "Alice");
    savedUser.setId(UUID.randomUUID());
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // Act
    RegisterResponse response = authService.registerUser(request);

    // Assert
    assertEquals("alice", response.username());
    assertEquals(AppConstants.ROLE_ADMIN, response.role());
  }
}
