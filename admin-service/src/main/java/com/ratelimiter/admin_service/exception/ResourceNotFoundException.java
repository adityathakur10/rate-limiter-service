package com.ratelimiter.admin_service.exception;

import com.ratelimiter.common.exception.RateLimiterException;

public class ResourceNotFoundException extends RateLimiterException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
