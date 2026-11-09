package com.ratelimiter.core.service;

import com.ratelimiter.common.cache.CacheKeys;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.entity.Rule;
import com.ratelimiter.core.redis.RedisCache;
import com.ratelimiter.core.repository.RuleReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Loads a tenant's rules. Cache-aside: Redis first ({@code rules:{tenantId}}, 5 minutes),
 * Postgres on a miss, then the result is cached. Admin deletes the Redis key whenever a rule
 * changes, so the next call reloads fresh rules.
 */
@Component
public class RuleResolver {

    private static final Logger log = LoggerFactory.getLogger(RuleResolver.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<RuleDto>> RULE_LIST = new TypeReference<>() {
    };

    private final RedisCache cache;
    private final RuleReadRepository rules;
    private final JsonMapper jsonMapper;

    public RuleResolver(RedisCache cache, RuleReadRepository rules, JsonMapper jsonMapper) {
        this.cache = cache;
        this.rules = rules;
        this.jsonMapper = jsonMapper;
    }

    /**
     * All rules of the tenant; an empty list when it has none. An empty list is cached too, so a
     * tenant without rules does not hit Postgres on every request.
     */
    public Mono<List<RuleDto>> rulesFor(UUID tenantId) {
        String cacheKey = CacheKeys.rules(tenantId);
        return cache.get(cacheKey)
                .flatMap(json -> parse(cacheKey, json))
                .switchIfEmpty(Mono.defer(() -> loadAndCache(cacheKey, tenantId)));
    }

    // a value that cannot be read is treated as a miss, so it gets reloaded and overwritten
    private Mono<List<RuleDto>> parse(String cacheKey, String json) {
        try {
            return Mono.justOrEmpty(jsonMapper.readValue(json, RULE_LIST));
        } catch (JacksonException e) {
            log.warn("Unreadable cache value under {}; reloading from Postgres", cacheKey);
            return Mono.empty();
        }
    }

    private Mono<List<RuleDto>> loadAndCache(String cacheKey, UUID tenantId) {
        return rules.findByTenantId(tenantId)
                .map(found -> found.stream().map(RuleResolver::toDto).toList())
                .flatMap(dtos -> cache
                        .set(cacheKey, jsonMapper.writeValueAsString(dtos), TTL)
                        .thenReturn(dtos));
    }

    // the timestamps are not needed on the hot path, so they are left out of the cached copy
    private static RuleDto toDto(Rule rule) {
        return new RuleDto(rule.getId(), rule.getTenantId(), rule.getEndpoint(), rule.getMethod(),
                rule.getLimit(), rule.getWindowSeconds(), rule.getAlgorithm(), null, null);
    }
}
