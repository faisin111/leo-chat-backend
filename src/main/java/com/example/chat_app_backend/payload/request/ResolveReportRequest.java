package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
    @NotBlank @Size(max = 2000) String resolution, @Size(max = 100) String action) {}
