package com.example.chat_app_backend.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.UserBlockId> {
  List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
}
