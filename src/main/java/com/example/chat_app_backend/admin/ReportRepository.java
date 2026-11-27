package com.example.chat_app_backend.admin;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
  long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

  Page<Report> findByStatus(String status, Pageable pageable);
}
