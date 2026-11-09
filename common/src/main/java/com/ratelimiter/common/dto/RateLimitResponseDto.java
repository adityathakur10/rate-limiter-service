package com.ratelimiter.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * resetAt is set on allowed responses, retryAfterSeconds on denied ones; the other is omitted from
 * the JSON. When no rule applies to the request, only {@code allowed} is present.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RateLimitResponseDto(
        boolean allowed,
        Long remaining,
        Instant resetAt,
        Long retryAfterSeconds) {
}
