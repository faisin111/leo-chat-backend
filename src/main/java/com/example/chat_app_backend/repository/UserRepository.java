package com.example.chat_app_backend.repository;

import com.example.chat_app_backend.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  Boolean existsByUsername(String username);

  Boolean existsByEmail(String email);

  Boolean existsByRole(String role);

  org.springframework.data.domain.Page<User>
      findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
          String username, String displayName, org.springframework.data.domain.Pageable pageable);
}
