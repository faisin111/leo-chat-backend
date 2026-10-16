package com.example.chat_app_backend.repository;

import com.example.chat_app_backend.model.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query("select a from AuditLog a where (:actor is null or a.actorId = :actor) and (:action is null or a.action = :action) and (:from is null or a.createdAt >= :from) and (:to is null or a.createdAt <= :to)")
    Page<AuditLog> findFiltered(@Param("actor") UUID actor, @Param("action") String action,
        @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);
    Page<AuditLog> findByAction(String action, Pageable pageable);
    Page<AuditLog> findByActorIdAndAction(UUID actorId, String action, Pageable pageable);
}
