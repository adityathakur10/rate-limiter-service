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
class FixedWindowLimiterTest {

    @Autowired
    private FixedWindowLimiter limiter;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private ReactiveRedisConnectionFactory connectionFactory;

    private static RuleDto rule(UUID tenantId, int limit, int windowSeconds) {
        return new RuleDto(null, tenantId, "/pay", "POST", limit, windowSeconds,
                AlgorithmType.FIXED_WINDOW, null, null);
    }

    private Decision check(UUID tenantId, RuleDto rule) {
        return limiter.check(tenantId, rule).block(Duration.ofSeconds(10));
    }

    // window maths (pure)

    @Test
    void windowStartRoundsDownToTheWindowBoundary() {
        assertThat(FixedWindowLimiter.windowStart(0, 60)).isEqualTo(0);
        assertThat(FixedWindowLimiter.windowStart(59, 60)).isEqualTo(0);
        assertThat(FixedWindowLimiter.windowStart(60, 60)).isEqualTo(60);
        assertThat(FixedWindowLimiter.windowStart(119, 60)).isEqualTo(60);
        assertThat(FixedWindowLimiter.windowStart(120, 60)).isEqualTo(120);
        assertThat(FixedWindowLimiter.windowStart(1793423575L, 60)).isEqualTo(1793423520L);
        assertThat(FixedWindowLimiter.windowStart(1793423575L, 1)).isEqualTo(1793423575L);
        assertThat(FixedWindowLimiter.windowStart(1793423575L, 3600)).isEqualTo(1793422800L);
    }

    @Test
    void keyUsesTheRulesMethodAndEndpointNotTheRequests() {
        UUID tenant = UUID.randomUUID();
        RuleDto wildcard = new RuleDto(null, tenant, "*", "*", 5, 60, AlgorithmType.FIXED_WINDOW, null, null);
        assertThat(FixedWindowLimiter.key(tenant, wildcard, 1200L))
                .isEqualTo("rl:fixed_window:" + tenant + ":*:*:1200");
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

        // the counter keeps counting past the limit, remaining never goes negative
        Decision deniedAgain = check(tenant, rule);
        assertThat(deniedAgain.allowed()).isFalse();
        assertThat(deniedAgain.remaining()).isZero();
    }

    @Test
    void counterKeyHasTheDocumentedFormatAndExpires() {
        UUID tenant = UUID.randomUUID();
        check(tenant, rule(tenant, 5, 60));

        List<String> keys = redis.scan(ScanOptions.scanOptions().match("rl:fixed_window:" + tenant + ":*").build())
                .collectList().block(Duration.ofSeconds(10));

        assertThat(keys).hasSize(1);
        String key = keys.get(0);
        assertThat(key).matches("rl:fixed_window:" + tenant + ":POST:/pay:\\d+");
        long windowStart = Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
        assertThat(windowStart % 60).isZero();

        Duration ttl = redis.getExpire(key).block(Duration.ofSeconds(10));
        assertThat(ttl).isNotNull();
        assertThat(ttl.toSeconds()).isBetween(1L, 60L);
    }

    @Test
    void wildcardAndSpecificRulesUseSeparateCounters() {
        UUID tenant = UUID.randomUUID();
        RuleDto specific = rule(tenant, 1, 3600);
        RuleDto wildcard = new RuleDto(null, tenant, "*", "*", 1, 3600, AlgorithmType.FIXED_WINDOW, null, null);

        assertThat(check(tenant, specific).allowed()).isTrue();
        assertThat(check(tenant, specific).allowed()).isFalse();
        assertThat(check(tenant, wildcard).allowed()).isTrue();
    }

    @Test
    void differentTenantsDoNotShareACounter() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(check(first, rule(first, 1, 3600)).allowed()).isTrue();
        assertThat(check(first, rule(first, 1, 3600)).allowed()).isFalse();
        assertThat(check(second, rule(second, 1, 3600)).allowed()).isTrue();
    }

    @Test
    void twoHundredParallelCallsAtLimitFiftyAllowExactlyFifty() {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 50, 3600); // long window so a boundary cannot fall mid-test

        List<Decision> decisions = Flux.range(0, 200)
                .flatMap(i -> limiter.check(tenant, rule).subscribeOn(Schedulers.parallel()), 200)
                .collectList()
                .block(Duration.ofSeconds(30));

        assertThat(decisions).hasSize(200);
        assertThat(decisions.stream().filter(Decision::allowed).count()).isEqualTo(50);
        assertThat(decisions.stream().filter(d -> !d.allowed()).count()).isEqualTo(150);

        List<String> keys = redis.scan(ScanOptions.scanOptions().match("rl:fixed_window:" + tenant + ":*").build())
                .collectList().block(Duration.ofSeconds(10));
        assertThat(keys).hasSize(1);
        assertThat(redis.opsForValue().get(keys.get(0)).block(Duration.ofSeconds(10))).isEqualTo("200");
    }

    @Test
    void aNewWindowStartsFromZeroOnceTheOldOneHasElapsed() throws InterruptedException {
        UUID tenant = UUID.randomUUID();
        RuleDto rule = rule(tenant, 1, 1);

        // start just after a second boundary so the two calls below share one 1-second window
        long millis = System.currentTimeMillis() % 1000;
        if (millis > 300) {
            Thread.sleep(1000 - millis + 20);
        }

        assertThat(check(tenant, rule).allowed()).isTrue();
        assertThat(check(tenant, rule).allowed()).isFalse();

        Thread.sleep(1100);

        Decision next = check(tenant, rule);
        assertThat(next.allowed()).isTrue();
        assertThat(next.remaining()).isZero();
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
