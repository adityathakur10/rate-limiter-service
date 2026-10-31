package com.ratelimiter.common.dto;

import com.ratelimiter.common.algorithm.AlgorithmType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RuleDto(
        UUID id,
        @NotNull UUID tenantId,
        @NotBlank String endpoint,
        @NotBlank String method,
        @Min(1) int limit,
        @Min(1) int windowSeconds,
        AlgorithmType algorithm,
        Instant createdAt,
        Instant updatedAt) {
}
