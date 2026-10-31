package com.ratelimiter.core.service;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RateLimitRequestDto;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.core.algorithm.AlgorithmRegistry;
import com.ratelimiter.core.algorithm.Decision;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RateLimitService {

    private final AlgorithmRegistry registry;

    public RateLimitService(AlgorithmRegistry registry) {
        this.registry = registry;
    }

    // TEMPORARY (step 2.9): one hardcoded rule for every tenant, and the api key is not
    // checked yet. Step 3.7 replaces both with real key validation and rule lookup.
    public Mono<Decision> check(RateLimitRequestDto request) {
        RuleDto rule = new RuleDto(null, request.tenantId(), "*", "*", 5, 60,
                AlgorithmType.FIXED_WINDOW, null, null);
        return registry.resolve(rule).check(request.tenantId(), rule);
    }
}
