package com.ratelimiter.core.algorithm;

import java.time.Instant;

/** Outcome of one rate-limit check, the same shape for every algorithm. */
public record Decision(
        boolean allowed,
        int limit,
        long remaining,
        Instant resetAt,
        long retryAfterSeconds,
        boolean degraded) {

    /** A normal decision: the check ran. */
    public Decision(boolean allowed, int limit, long remaining, Instant resetAt, long retryAfterSeconds) {
        this(allowed, limit, remaining, resetAt, retryAfterSeconds, false);
    }

    /** No rule applies to the request: it is allowed and nothing is counted. */
    public static Decision unlimited() {
        return new Decision(true, 0, 0, null, 0, false);
    }

    /**
     * The check could not run (Redis is down), so the request is allowed unchecked. Like
     * {@link #unlimited()} it has no window, but the caller is told the check was skipped.
     */
    public static Decision failedOpen() {
        return new Decision(true, 0, 0, null, 0, true);
    }

    /** False for {@link #unlimited()} and {@link #failedOpen()}, the decisions that have no window. */
    public boolean isLimited() {
        return resetAt != null;
    }
}
