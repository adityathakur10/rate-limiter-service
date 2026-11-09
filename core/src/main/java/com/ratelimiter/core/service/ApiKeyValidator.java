package com.ratelimiter.core.service;

import com.ratelimiter.common.cache.CacheKeys;
import com.ratelimiter.common.exception.UnauthorizedException;
import com.ratelimiter.core.redis.RedisCache;
import com.ratelimiter.core.repository.TenantReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * Checks that an API key belongs to the tenant named in the request. Cache-aside: Redis first
 * ({@code tenant:{apiKey}}, 10 minutes), Postgres on a miss, then the result is cached.
 * Unknown keys are not cached (see "Unknown API keys are never cached" in PLAN.md).
 */
@Component
public class ApiKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyValidator.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    /** What is cached: the only thing the hot path needs to know about a tenant. */
    record CachedTenant(UUID tenantId) {
    }

    private final RedisCache cache;
    private final TenantReadRepository tenants;
    private final JsonMapper jsonMapper;

    public ApiKeyValidator(RedisCache cache, TenantReadRepository tenants, JsonMapper jsonMapper) {
        this.cache = cache;
        this.tenants = tenants;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Completes when the key belongs to the tenant, otherwise fails with {@link UnauthorizedException}.
     * An unknown key and a key of another tenant give the same error, so a caller cannot tell
     * which keys exist.
     */
    public Mono<Void> validate(UUID tenantId, String apiKey) {
        return tenantIdFor(apiKey)
                .filter(tenantId::equals)
                .switchIfEmpty(Mono.error(() -> new UnauthorizedException("Invalid API key")))
                .then();
    }

    private Mono<UUID> tenantIdFor(String apiKey) {
        String cacheKey = CacheKeys.tenant(apiKey);
        return cache.get(cacheKey)
                .flatMap(this::parse)
                .switchIfEmpty(Mono.defer(() -> loadAndCache(cacheKey, apiKey)))
                .map(CachedTenant::tenantId);
    }

    // a value that cannot be read is treated as a miss, so it gets reloaded and overwritten
    // (the cache key is not logged: it contains the API key)
    private Mono<CachedTenant> parse(String json) {
        try {
            return Mono.justOrEmpty(jsonMapper.readValue(json, CachedTenant.class));
        } catch (JacksonException e) {
            log.warn("Unreadable cached tenant; reloading from Postgres");
            return Mono.empty();
        }
    }

    private Mono<CachedTenant> loadAndCache(String cacheKey, String apiKey) {
        return tenants.findByApiKey(apiKey)
                .map(tenant -> new CachedTenant(tenant.getId()))
                .flatMap(cached -> cache
                        .set(cacheKey, jsonMapper.writeValueAsString(cached), TTL)
                        .thenReturn(cached));
    }
}
