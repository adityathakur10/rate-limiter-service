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

/**
 * Remembers the time of every allowed request in a Redis sorted set and counts the ones in the
 * last windowSeconds, measured from now. Exact, with no boundary burst, but memory grows with the limit.
 */
@Component
public class SlidingWindowLogLimiter implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:" + AlgorithmType.SLIDING_WINDOW_LOG.value() + ":";

    private final LuaScriptExecutor executor;
    private final RedisScript<List<Long>> script = loadScript();

    public SlidingWindowLogLimiter(LuaScriptExecutor executor) {
        this.executor = executor;
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.SLIDING_WINDOW_LOG;
    }

    @Override
    public Mono<Decision> check(UUID tenantId, RuleDto rule) {
        // defer so a fresh member id is made per subscription, not once when the Mono is built.
        // Time is not read here: the script uses Redis's clock.
        return Mono.defer(() -> {
            List<String> keys = List.of(key(tenantId, rule));
            List<String> args = List.of(
                    String.valueOf(rule.limit()),
                    String.valueOf(windowMillis(rule)),
                    UUID.randomUUID().toString());
            return executor.execute(script, keys, args).map(reply -> decide(rule, reply));
        });
    }

    // The matched rule's method and endpoint, not the raw request's, so a wildcard rule shares one log.
    // No window-start suffix: the window slides, so there is one log per rule.
    static String key(UUID tenantId, RuleDto rule) {
        return KEY_PREFIX + tenantId + ":" + rule.method() + ":" + rule.endpoint();
    }

    static long windowMillis(RuleDto rule) {
        return rule.windowSeconds() * 1000L;
    }

    /** reply = {allowed (1/0), count, oldestMs, nowMs}, as returned by sliding_window_log.lua. */
    static Decision decide(RuleDto rule, List<Long> reply) {
        boolean allowed = reply.get(0) == 1L;
        long count = reply.get(1);
        long oldestMs = reply.get(2);
        long nowMs = reply.get(3);

        long remaining = Math.max(0, rule.limit() - count);
        // when the oldest entry slides out, a slot frees up
        long resetAtMs = oldestMs + windowMillis(rule);
        long retryAfter = Math.max(1, ceilDiv(resetAtMs - nowMs, 1000));
        return new Decision(allowed, rule.limit(), remaining, Instant.ofEpochMilli(resetAtMs), retryAfter);
    }

    private static long ceilDiv(long value, long divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> loadScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/sliding_window_log.lua"));
        script.setResultType((Class) List.class);
        return script;
    }
}
