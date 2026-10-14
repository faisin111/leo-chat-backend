package com.example.chat_app_backend.auth.bootstrap;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.common.constants.AppConstants;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true")
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapRunner implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final AdminBootstrapProperties properties;

  public AdminBootstrapRunner(
      UserRepository users, PasswordEncoder passwords, AdminBootstrapProperties properties) {
    this.users = users;
    this.passwords = passwords;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (users.existsByRole(AppConstants.ROLE_ADMIN)) return;
    if (users.existsByUsername(properties.username()) || users.existsByEmail(properties.email())) {
      throw new IllegalStateException(
          "Bootstrap identity belongs to an existing account; automatic promotion is forbidden");
    }
    User admin =
        new User(
            properties.username(),
            properties.email(),
            passwords.encode(properties.password()),
            AppConstants.ROLE_ADMIN,
            properties.username());
    admin.setMustChangePassword(true);
    admin.setEmailVerified(true);
    users.save(admin);
  }
}
