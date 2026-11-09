package com.ratelimiter.core.service;

import com.ratelimiter.common.dto.RateLimitRequestDto;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.core.algorithm.AlgorithmRegistry;
import com.ratelimiter.core.algorithm.Decision;
import com.ratelimiter.core.redis.RedisUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * One rate-limit check: validate the API key, find the rule that applies to the request, run
 * the rule's algorithm. A request that no rule applies to is allowed, and so is one the counter
 * could not be updated for because Redis is down (fail open: a limiter outage must not take the
 * callers down with it).
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final ApiKeyValidator apiKeyValidator;
    private final RuleResolver ruleResolver;
    private final AlgorithmRegistry registry;

    public RateLimitService(ApiKeyValidator apiKeyValidator, RuleResolver ruleResolver, AlgorithmRegistry registry) {
        this.apiKeyValidator = apiKeyValidator;
        this.ruleResolver = ruleResolver;
        this.registry = registry;
    }

    /** Fails with UnauthorizedException when the key does not belong to the tenant. */
    public Mono<Decision> check(RateLimitRequestDto request) {
        return apiKeyValidator.validate(request.tenantId(), request.apiKey())
                // deferred so the rules are only looked up once the key has been accepted
                .then(Mono.defer(() -> ruleResolver.rulesFor(request.tenantId())))
                .flatMap(rules -> RuleMatcher.match(rules, request.endpoint(), request.method())
                        .map(rule -> runAlgorithm(request, rule))
                        .orElseGet(() -> Mono.just(Decision.unlimited())));
    }

    private Mono<Decision> runAlgorithm(RateLimitRequestDto request, RuleDto rule) {
        return registry.resolve(rule).check(request.tenantId(), rule)
                .onErrorResume(RedisUnavailableException.class, e -> {
                    log.warn("Redis is unavailable, allowing the request: {}", e.getCause() != null ? e.getCause().toString() : e.toString());
                    return Mono.just(Decision.failedOpen());
                });
    }
}
