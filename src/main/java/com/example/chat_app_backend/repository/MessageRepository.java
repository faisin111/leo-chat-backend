package com.example.chat_app_backend.repository;

import com.example.chat_app_backend.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByChat_IdOrderBySentAtAsc(UUID chatId);
}
