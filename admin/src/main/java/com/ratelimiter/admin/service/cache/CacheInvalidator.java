package com.ratelimiter.admin.service.cache;

import java.util.UUID;

public interface CacheInvalidator {

    /**
     * Drops the cached rules of a tenant so core reloads them from Postgres. Called from inside
     * the rule write; the real implementation waits for the commit before deleting.
     */
    void invalidateRules(UUID tenantId);
}
