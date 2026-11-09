package com.ratelimiter.common.cache;

import java.util.UUID;

/**
 * Redis key names for cached configuration, shared so core (which reads and writes them) and
 * admin (which deletes them when a rule changes) cannot drift apart.
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** The tenant that owns an API key. The key name contains the API key, so never log it. */
    public static String tenant(String apiKey) {
        return "tenant:" + apiKey;
    }

    /** All rules of a tenant. */
    public static String rules(UUID tenantId) {
        return "rules:" + tenantId;
    }
}
