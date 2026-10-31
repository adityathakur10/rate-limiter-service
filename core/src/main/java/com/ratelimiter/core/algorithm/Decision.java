package com.ratelimiter.core.algorithm;

import java.time.Instant;

/** Outcome of one rate-limit check, the same shape for every algorithm. */
public record Decision(
        boolean allowed,
        int limit,
        long remaining,
        Instant resetAt,
        long retryAfterSeconds) {
}
