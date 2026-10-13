package com.example.chat_app_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.chat_app_backend.auth.bootstrap.AdminBootstrapProperties;
import com.example.chat_app_backend.auth.bootstrap.AdminBootstrapRunner;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapRunnerTest {
  UserRepository users = mock(UserRepository.class);
  PasswordEncoder passwords = mock(PasswordEncoder.class);
  AdminBootstrapRunner runner =
      new AdminBootstrapRunner(
          users,
          passwords,
          new AdminBootstrapProperties("admin", "admin@test.com", "InitialPass123!"));

  @Test
  void createsOnlyBootstrapAdminWithHashedPassword() {
    when(passwords.encode("InitialPass123!")).thenReturn("hashed");
    runner.run(null);
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo("ROLE_ADMIN");
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    assertThat(saved.getValue().isMustChangePassword()).isTrue();
  }

  @Test
  void existingAdminIsNeverRecreated() {
    when(users.existsByRole("ROLE_ADMIN")).thenReturn(true);
    runner.run(null);
    verifyNoInteractions(passwords);
  }

  @Test
  void neverPromotesExistingPublicAccount() {
    when(users.existsByUsername("admin")).thenReturn(true);
    assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(passwords);
  }
}
