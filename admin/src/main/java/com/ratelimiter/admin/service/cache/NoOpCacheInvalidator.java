package com.ratelimiter.admin.service.cache;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Stage 1 stand-in; replaced by the Redis version in step 3.9. */
@Component
public class NoOpCacheInvalidator implements CacheInvalidator {

    @Override
    public void invalidateRules(UUID tenantId) {
        // nothing to invalidate until Redis is added
    }
}
