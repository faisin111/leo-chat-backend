package com.example.chat_app_backend.repository;

import com.example.chat_app_backend.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {
    List<Chat> findByParticipants_Id(UUID userId);
}
