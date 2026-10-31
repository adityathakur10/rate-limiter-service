package com.ratelimiter.admin.service.cache;

import java.util.UUID;

public interface CacheInvalidator {

    void invalidateRules(UUID tenantId);
}
