package com.ratelimiter.common.dto;

import java.time.Instant;

/** resetAt is set on allowed responses, retryAfterSeconds on denied ones; the other is null. */
public record RateLimitResponseDto(
        boolean allowed,
        long remaining,
        Instant resetAt,
        Long retryAfterSeconds) {
}
