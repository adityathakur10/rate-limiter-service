package com.ratelimiter.core.service;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RateLimitRequestDto;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.exception.UnauthorizedException;
import com.ratelimiter.core.algorithm.AlgorithmRegistry;
import com.ratelimiter.core.algorithm.Decision;
import com.ratelimiter.core.algorithm.RateLimitAlgorithm;
import com.ratelimiter.core.redis.RedisUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// the order of the steps and what each one hands to the next; no Spring, Redis or Postgres
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private ApiKeyValidator apiKeyValidator;
    @Mock
    private RuleResolver ruleResolver;
    @Mock
    private AlgorithmRegistry registry;
    @Mock
    private RateLimitAlgorithm algorithm;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(apiKeyValidator, ruleResolver, registry);
    }

    private RateLimitRequestDto request(String endpoint, String method) {
        return new RateLimitRequestDto(tenantId, "rl_key", endpoint, method);
    }

    private RuleDto rule(String endpoint, String method, int limit) {
        return new RuleDto(UUID.randomUUID(), tenantId, endpoint, method, limit, 60,
                AlgorithmType.FIXED_WINDOW, null, null);
    }

    private Decision check(RateLimitRequestDto request) {
        return service.check(request).block(Duration.ofSeconds(5));
    }

    @Test
    void runsTheMatchedRulesAlgorithmAndReturnsItsDecision() {
        RuleDto specific = rule("/pay", "POST", 5);
        RuleDto catchAll = rule("*", "*", 100);
        Decision expected = new Decision(true, 5, 4, Instant.now().plusSeconds(30), 30);
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of(catchAll, specific)));
        when(registry.resolve(specific)).thenReturn(algorithm);
        when(algorithm.check(tenantId, specific)).thenReturn(Mono.just(expected));

        assertThat(check(request("/pay", "POST"))).isSameAs(expected);
    }

    @Test
    void aRequestNoRuleAppliesToIsAllowedAndNothingIsCounted() {
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of(rule("/pay", "POST", 5))));

        Decision decision = check(request("/orders", "GET"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.isLimited()).isFalse();
        assertThat(decision.degraded()).isFalse();   // nothing went wrong, so nothing to flag
        verifyNoInteractions(registry);
    }

    @Test
    void aTenantWithoutRulesIsAllowed() {
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of()));

        assertThat(check(request("/pay", "POST")).allowed()).isTrue();
    }

    @Test
    void aRejectedKeyStopsTheCheckBeforeAnyRuleIsLookedUp() {
        when(apiKeyValidator.validate(tenantId, "rl_key"))
                .thenReturn(Mono.error(new UnauthorizedException("Invalid API key")));

        assertThatThrownBy(() -> check(request("/pay", "POST")))
                .isInstanceOf(UnauthorizedException.class);

        verify(ruleResolver, never()).rulesFor(any());
        verifyNoInteractions(registry);
    }

    @Test
    void aRedisOutageDuringCountingFailsOpen() {
        RuleDto rule = rule("/pay", "POST", 1);
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of(rule)));
        when(registry.resolve(rule)).thenReturn(algorithm);
        when(algorithm.check(tenantId, rule))
                .thenReturn(Mono.error(new RedisUnavailableException("down", new RuntimeException("refused"))));

        Decision decision = check(request("/pay", "POST"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.isLimited()).isFalse();
        assertThat(decision.degraded()).isTrue();   // the caller is told the check was skipped
    }

    @Test
    void otherAlgorithmErrorsAreNotSwallowed() {
        RuleDto rule = rule("/pay", "POST", 1);
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of(rule)));
        when(registry.resolve(rule)).thenReturn(algorithm);
        when(algorithm.check(tenantId, rule)).thenReturn(Mono.error(new IllegalStateException("a bug")));

        assertThatThrownBy(() -> check(request("/pay", "POST"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDeniedDecisionIsPassedThrough() {
        RuleDto rule = rule("/pay", "POST", 1);
        Decision denied = new Decision(false, 1, 0, Instant.now().plusSeconds(10), 10);
        when(apiKeyValidator.validate(tenantId, "rl_key")).thenReturn(Mono.empty());
        when(ruleResolver.rulesFor(tenantId)).thenReturn(Mono.just(List.of(rule)));
        when(registry.resolve(rule)).thenReturn(algorithm);
        when(algorithm.check(tenantId, rule)).thenReturn(Mono.just(denied));

        assertThat(check(request("/pay", "POST")).allowed()).isFalse();
    }
}
