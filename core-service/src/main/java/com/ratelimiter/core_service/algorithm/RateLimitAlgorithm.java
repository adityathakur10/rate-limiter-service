package com.ratelimiter.core_service.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * One rate-limiting strategy. Implement it as a Spring bean and the {@link AlgorithmRegistry}
 * picks it up. Each algorithm builds its own Redis key under the {@code rl:{type}:} prefix.
 */
public interface RateLimitAlgorithm {

    AlgorithmType type();

    Mono<Decision> check(UUID tenantId, RuleDto rule);
}
