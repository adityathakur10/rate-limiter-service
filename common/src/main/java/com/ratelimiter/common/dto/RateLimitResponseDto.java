package com.ratelimiter.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** resetAt is set on allowed responses, retryAfterSeconds on denied ones; the other is omitted from the JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RateLimitResponseDto(
        boolean allowed,
        long remaining,
        Instant resetAt,
        Long retryAfterSeconds) {
}
