package com.ratelimiter.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RateLimitRequestDto(
        @NotNull UUID tenantId,
        @NotBlank String apiKey,
        @NotBlank String endpoint,
        @NotBlank String method
) {}
