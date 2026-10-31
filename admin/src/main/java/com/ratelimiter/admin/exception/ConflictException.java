package com.ratelimiter.admin.exception;

import com.ratelimiter.common.exception.RateLimiterException;

public class ConflictException extends RateLimiterException {

    public ConflictException(String message) {
        super(message);
    }
}
