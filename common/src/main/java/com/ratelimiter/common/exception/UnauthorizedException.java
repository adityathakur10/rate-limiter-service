package com.ratelimiter.common.exception;

public class UnauthorizedException extends RateLimiterException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
