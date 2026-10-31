package com.ratelimiter.core_service.redis;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Runs a Lua script and returns its numeric reply. Spring sends EVALSHA and falls back to
 * EVAL on NOSCRIPT by itself, so this class only wraps the call. Each algorithm reads
 * its own reply shape.
 */
@Component
public class LuaScriptExecutor {

    private final ReactiveRedisTemplate<String, String> redis;

    public LuaScriptExecutor(ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public Mono<List<Long>> execute(RedisScript<List<Long>> script, List<String> keys, List<String> args) {
        return redis.execute(script, keys, args).next();
    }
}
