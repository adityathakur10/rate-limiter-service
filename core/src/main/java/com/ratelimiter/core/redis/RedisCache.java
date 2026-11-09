package com.ratelimiter.core.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Best-effort cache access: a Redis failure is a cache miss, never an error. The data behind the
 * cache lives in Postgres, so callers just read it from there when Redis cannot answer.
 * Keys are not logged: the tenant key contains an API key.
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisCache(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** The cached text; empty on a miss, and also when Redis fails. */
    public Mono<String> get(String key) {
        return redis.opsForValue().get(key)
                .onErrorResume(e -> {
                    log.warn("Redis read failed, treating it as a cache miss: {}", e.toString());
                    return Mono.empty();
                });
    }

    /** True when stored; false when Redis fails, so the caller carries on without caching. */
    public Mono<Boolean> set(String key, String value, Duration ttl) {
        return redis.opsForValue().set(key, value, ttl)
                .onErrorResume(e -> {
                    log.warn("Redis write failed, carrying on without caching: {}", e.toString());
                    return Mono.just(false);
                });
    }
}
