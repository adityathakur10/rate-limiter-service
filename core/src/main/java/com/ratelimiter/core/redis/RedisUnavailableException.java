package com.ratelimiter.core.redis;

/**
 * Redis could not run a command (down, timed out, refused). A plain RuntimeException, not a
 * RateLimiterException, so that if it ever escaped it would be a 500, not a 400.
 */
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
