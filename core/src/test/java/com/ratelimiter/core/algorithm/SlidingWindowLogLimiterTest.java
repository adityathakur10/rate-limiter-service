package com.ratelimiter.core.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.core.CoreTestApplication;
import com.ratelimiter.core.RedisContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CoreTestApplication.class)
@Import(RedisContainerConfig.class)
class SlidingWindowLogLimiterTest {

    @Autowired
    private SlidingWindowLogLimiter limiter;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private ReactiveRedisConnectionFactory connectionFactory;

    private static RuleDto rule(UUID tenantId, int limit, int windowSeconds) {
        return new RuleDto(null, tenantId, "/pay", "POST", limit, windowSeconds,
                AlgorithmType.SLIDING_WINDOW_LOG, null, null);
    }

    private Decision check(UUID tenantId, RuleDto rule) {
        return limiter.check(tenantId, rule).block(Duration.ofSeconds(10));
    }

    private long logSize(UUID tenantId, RuleDto rule) {
        Long size = redis.opsForZSet().size(SlidingWindowLogLimiter.key(tenantId, rule)).block(Duration.ofSeconds(10));
        return size == null ? 0 : size;
    }

    // pure parts

    @Test
    void keyUsesTheRulesMethodAndEndpointNotTheRequests() {
        UUID tenant = UUID.randomUUID();
        RuleDto wildcard = new RuleDto(null, tenant, "*", "*", 5, 60, AlgorithmType.SLIDING_WINDOW_LOG, null, null);
        assertThat(SlidingWindowLogLimiter.key(tenant, wildcard))
                .isEqualTo("rl:sliding_window_log:" + tenant + ":*:*");
    }

    @Test
    void decideTurnsTheScriptReplyIntoADecision() {
        RuleDto rule = rule(UUID.randomUUID(), 5, 10);

        // allowed, 2 of 5 used; oldest entry at t=1000, now t=4500 -> frees at 11000, 6.5s away
        Decision allowed = SlidingWindowLogLimiter.decide(rule, List.of(1L, 2L, 1000L, 4500L));
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.limit()).isEqualTo(5);
        assertThat(allowed.remaining()).isEqualTo(3);
        assertThat(allowed.resetAt()).isEqualTo(Instant.ofEpochMilli(11000));
        assertThat(allowed.retryAfterSeconds()).isEqualTo(7); // 6.5s rounds up

        Decision denied = SlidingWindowLogLimiter.decide(rule, List.of(0L, 5L, 1000L, 4500L));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
    }

    @Test
    void decideNeverReportsNegativeRemainingOrZeroRetry() {
        RuleDto rule = rule(UUID.randomUUID(), 2, 10);

        // count above the limit (limit lowered after the log filled): remaining clamps at 0
        assertThat(SlidingWindowLogLimiter.decide(rule, List.of(0L, 7L, 1000L, 4500L)).remaining()).isZero();

        // the oldest entry is about to expire: retry-after is at least 1
        assertThat(SlidingWindowLogLimiter.decide(rule, List.of(0L, 2L, 1000L, 10999L)).retryAfterSeconds())
                .isEqualTo(1);
    }

    // behaviour against a real Redis

    @Test
    void firstCallsAreAllowedAndTheOneOverTheLimitIsDenied() {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 3, 3600);

        for (int expectedRemaining = 2; expectedRemaining >= 0; expectedRemaining--) {
            Decision decision = check(tenant, rule);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(3);
            assertThat(decision.remaining()).isEqualTo(expectedRemaining);
        }

        Decision denied = check(tenant, rule);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 3600L);
        assertThat(denied.resetAt()).isAfter(Instant.now());
    }

    @Test
    void deniedRequestsAreNotRecorded() {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 2, 3600);

        check(tenant, rule);
        check(tenant, rule);
        for (int i = 0; i < 10; i++) {
            assertThat(check(tenant, rule).allowed()).isFalse();
        }

        // ten denials later the log still holds only the two allowed requests
        assertThat(logSize(tenant, rule)).isEqualTo(2);
    }

    @Test
    void thereIsNoBurstAcrossAWindowBoundary() throws InterruptedException {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 2, 2);

        assertThat(check(tenant, rule).allowed()).isTrue();
        assertThat(check(tenant, rule).allowed()).isTrue();

        // 1.1s later the first two requests are still inside the sliding 2s window
        Thread.sleep(1100);
        assertThat(check(tenant, rule).allowed()).isFalse();

        // 2.2s after the first two they have slid out, so there is room again
        Thread.sleep(1100);
        Decision next = check(tenant, rule);
        assertThat(next.allowed()).isTrue();
        assertThat(next.remaining()).isEqualTo(1);
    }

    @Test
    void logKeyHasTheDocumentedFormatAndExpires() {
        UUID tenant = UUID.randomUUID();
        check(tenant, rule(tenant, 5, 60));

        List<String> keys = redis.scan(ScanOptions.scanOptions().match("rl:sliding_window_log:" + tenant + ":*").build())
                .collectList().block(Duration.ofSeconds(10));

        assertThat(keys).containsExactly("rl:sliding_window_log:" + tenant + ":POST:/pay");

        Duration ttl = redis.getExpire(keys.get(0)).block(Duration.ofSeconds(10));
        assertThat(ttl).isNotNull();
        assertThat(ttl.toSeconds()).isBetween(1L, 60L);
    }

    @Test
    void wildcardAndSpecificRulesUseSeparateLogs() {
        UUID tenant = UUID.randomUUID();
        RuleDto specific = rule(tenant, 1, 3600);
        RuleDto wildcard = new RuleDto(null, tenant, "*", "*", 1, 3600, AlgorithmType.SLIDING_WINDOW_LOG, null, null);

        assertThat(check(tenant, specific).allowed()).isTrue();
        assertThat(check(tenant, specific).allowed()).isFalse();
        assertThat(check(tenant, wildcard).allowed()).isTrue();
    }

    @Test
    void differentTenantsDoNotShareALog() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(check(first, rule(first, 1, 3600)).allowed()).isTrue();
        assertThat(check(first, rule(first, 1, 3600)).allowed()).isFalse();
        assertThat(check(second, rule(second, 1, 3600)).allowed()).isTrue();
    }

    @Test
    void twoHundredParallelCallsAtLimitFiftyAllowExactlyFifty() {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 50, 3600);

        List<Decision> decisions = Flux.range(0, 200)
                .flatMap(i -> limiter.check(tenant, rule).subscribeOn(Schedulers.parallel()), 200)
                .collectList()
                .block(Duration.ofSeconds(30));

        assertThat(decisions).hasSize(200);
        assertThat(decisions.stream().filter(Decision::allowed).count()).isEqualTo(50);
        assertThat(decisions.stream().filter(d -> !d.allowed()).count()).isEqualTo(150);

        // 50 distinct members: calls in the same millisecond did not collapse into one entry
        assertThat(logSize(tenant, rule)).isEqualTo(50);
    }

    @Test
    void worksAgainAfterRedisForgetsTheScript() {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 10, 3600);

        assertThat(check(tenant, rule).remaining()).isEqualTo(9);

        // drops the cached script, so the next EVALSHA gets NOSCRIPT and must fall back to EVAL
        connectionFactory.getReactiveConnection().scriptingCommands().scriptFlush().block(Duration.ofSeconds(10));

        Decision afterFlush = check(tenant, rule);
        assertThat(afterFlush.allowed()).isTrue();
        assertThat(afterFlush.remaining()).isEqualTo(8);
    }
}
