package com.ratelimiter.core.algorithm;

import java.time.Instant;

/** Outcome of one rate-limit check, the same shape for every algorithm. */
public record Decision(
        boolean allowed,
        int limit,
        long remaining,
        Instant resetAt,
        long retryAfterSeconds) {

    /** No rule applies to the request: it is allowed and nothing is counted. */
    public static Decision unlimited() {
        return new Decision(true, 0, 0, null, 0);
    }

    /** False only for {@link #unlimited()}, the one decision that has no window. */
    public boolean isLimited() {
        return resetAt != null;
    }
}
