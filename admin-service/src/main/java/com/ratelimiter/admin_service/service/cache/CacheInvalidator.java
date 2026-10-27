package com.ratelimiter.admin_service.service.cache;

import java.util.UUID;

public interface CacheInvalidator {

    void invalidateRules(UUID tenantId);
}
