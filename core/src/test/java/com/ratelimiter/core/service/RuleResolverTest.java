package com.ratelimiter.core.service;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.entity.Rule;
import com.ratelimiter.core.CoreTestApplication;
import com.ratelimiter.core.RedisContainerConfig;
import com.ratelimiter.core.repository.RuleReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// real Redis, mocked Postgres: each test uses its own tenant id, so they do not see each other's cache
@SpringBootTest(classes = CoreTestApplication.class)
@Import(RedisContainerConfig.class)
class RuleResolverTest {

    @Autowired
    private RuleResolver resolver;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @MockitoBean
    private RuleReadRepository rules;

    private static Rule rule(UUID tenantId, String endpoint, String method, int limit, int windowSeconds) {
        Rule rule = new Rule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setTenantId(tenantId);
        rule.setEndpoint(endpoint);
        rule.setMethod(method);
        rule.setLimit(limit);
        rule.setWindowSeconds(windowSeconds);
        return rule;
    }

    private List<RuleDto> rulesFor(UUID tenantId) {
        return resolver.rulesFor(tenantId).block(Duration.ofSeconds(10));
    }

    @Test
    void aMissLoadsTheRulesAndCachesThemForFiveMinutes() {
        UUID tenantId = UUID.randomUUID();
        when(rules.findByTenantId(tenantId))
                .thenReturn(Mono.just(List.of(rule(tenantId, "/pay", "POST", 5, 60))));

        List<RuleDto> found = rulesFor(tenantId);

        assertThat(found).hasSize(1);
        assertThat(redis.opsForValue().get("rules:" + tenantId).block()).contains("/pay");
        assertThat(redis.getExpire("rules:" + tenantId).block())
                .isBetween(Duration.ofSeconds(290), Duration.ofMinutes(5));
    }

    @Test
    void aHitReturnsTheSameRulesWithoutTouchingPostgres() {
        UUID tenantId = UUID.randomUUID();
        when(rules.findByTenantId(tenantId)).thenReturn(Mono.just(List.of(
                rule(tenantId, "/pay", "POST", 5, 60),
                rule(tenantId, "*", "*", 100, 30))));

        List<RuleDto> fromPostgres = rulesFor(tenantId);
        List<RuleDto> fromCache = rulesFor(tenantId);

        assertThat(fromCache).isEqualTo(fromPostgres);
        assertThat(fromCache).anySatisfy(dto -> {
            assertThat(dto.tenantId()).isEqualTo(tenantId);
            assertThat(dto.endpoint()).isEqualTo("/pay");
            assertThat(dto.method()).isEqualTo("POST");
            assertThat(dto.limit()).isEqualTo(5);
            assertThat(dto.windowSeconds()).isEqualTo(60);
            assertThat(dto.algorithm()).isEqualTo(AlgorithmType.FIXED_WINDOW);
        });
        verify(rules, times(1)).findByTenantId(tenantId);
    }

    @Test
    void aTenantWithoutRulesGetsAnEmptyListAndItIsCached() {
        UUID tenantId = UUID.randomUUID();
        when(rules.findByTenantId(tenantId)).thenReturn(Mono.just(List.of()));

        assertThat(rulesFor(tenantId)).isEmpty();
        assertThat(rulesFor(tenantId)).isEmpty();

        assertThat(redis.opsForValue().get("rules:" + tenantId).block()).isEqualTo("[]");
        verify(rules, times(1)).findByTenantId(tenantId);
    }

    @Test
    void deletingTheCacheKeyMakesTheNextCallReloadFreshRules() {
        UUID tenantId = UUID.randomUUID();
        when(rules.findByTenantId(tenantId))
                .thenReturn(Mono.just(List.of(rule(tenantId, "/pay", "POST", 5, 60))));
        assertThat(rulesFor(tenantId).get(0).limit()).isEqualTo(5);

        // what admin does after a rule changes (step 3.10)
        when(rules.findByTenantId(tenantId))
                .thenReturn(Mono.just(List.of(rule(tenantId, "/pay", "POST", 10, 60))));
        redis.delete("rules:" + tenantId).block();

        assertThat(rulesFor(tenantId).get(0).limit()).isEqualTo(10);
    }

    @Test
    void anUnreadableCachedValueIsTreatedAsAMissAndOverwritten() {
        UUID tenantId = UUID.randomUUID();
        redis.opsForValue().set("rules:" + tenantId, "not json").block();
        when(rules.findByTenantId(tenantId))
                .thenReturn(Mono.just(List.of(rule(tenantId, "/pay", "POST", 5, 60))));

        assertThat(rulesFor(tenantId)).hasSize(1);

        verify(rules, times(1)).findByTenantId(tenantId);
        assertThat(redis.opsForValue().get("rules:" + tenantId).block()).contains("/pay");
    }
}
