package com.example.chat_app_backend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferOwnershipRequest(
    @NotNull UUID targetUserId, @NotBlank @Size(max = 120) String password) {}
