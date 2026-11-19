package com.example.chat_app_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
    @NotBlank @Size(max = 300) String resolution, @Size(max = 100) String action) {}
