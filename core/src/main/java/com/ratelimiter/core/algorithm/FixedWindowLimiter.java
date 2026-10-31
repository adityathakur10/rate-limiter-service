package com.ratelimiter.core.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.core.redis.LuaScriptExecutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Counts requests per fixed time slot: window start = floor(now / windowSeconds) * windowSeconds. */
@Component
public class FixedWindowLimiter implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:" + AlgorithmType.FIXED_WINDOW.value() + ":";

    private final LuaScriptExecutor executor;
    private final RedisScript<List<Long>> script = loadScript();

    public FixedWindowLimiter(LuaScriptExecutor executor) {
        this.executor = executor;
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.FIXED_WINDOW;
    }

    @Override
    public Mono<Decision> check(UUID tenantId, RuleDto rule) {
        // defer so "now" is read when the check runs, not when the Mono is built
        return Mono.defer(() -> {
            long now = Instant.now().getEpochSecond();
            long windowStart = windowStart(now, rule.windowSeconds());
            List<String> keys = List.of(key(tenantId, rule, windowStart));
            List<String> args = List.of(String.valueOf(rule.limit()), String.valueOf(rule.windowSeconds()));
            return executor.execute(script, keys, args)
                    .map(reply -> decide(rule, now, windowStart, reply.get(0)));
        });
    }

    static long windowStart(long epochSeconds, int windowSeconds) {
        return Math.floorDiv(epochSeconds, windowSeconds) * windowSeconds;
    }

    // The matched rule's method and endpoint, not the raw request's, so a wildcard rule shares one counter.
    static String key(UUID tenantId, RuleDto rule, long windowStart) {
        return KEY_PREFIX + tenantId + ":" + rule.method() + ":" + rule.endpoint() + ":" + windowStart;
    }

    private static Decision decide(RuleDto rule, long now, long windowStart, long count) {
        long windowEnd = windowStart + rule.windowSeconds();
        boolean allowed = count <= rule.limit();
        long remaining = Math.max(0, rule.limit() - count);
        // From the window end, not the key's TTL: the key is created at the first request,
        // so its TTL can run past the window end and would overstate the wait.
        long retryAfter = Math.max(1, windowEnd - now);
        return new Decision(allowed, rule.limit(), remaining, Instant.ofEpochSecond(windowEnd), retryAfter);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> loadScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/fixed_window.lua"));
        script.setResultType((Class) List.class);
        return script;
    }
}
