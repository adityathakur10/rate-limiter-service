package com.ratelimiter.common.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record TenantDto(
        UUID id,
        @NotBlank String name,
        String apiKey,
        Instant createdAt
) {}
