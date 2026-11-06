package com.ratelimiter.core.repository;

import com.ratelimiter.common.entity.Rule;
import com.ratelimiter.common.reactive.BlockingCall;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Read-only rule lookups for the hot path. Every query runs on the bounded-elastic pool. */
@Component
public class RuleReadRepository {

    private final RuleJpaRepository jpa;

    public RuleReadRepository(RuleJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** An empty list when the tenant has no rules. */
    public Mono<List<Rule>> findByTenantId(UUID tenantId) {
        return BlockingCall.call(() -> jpa.findByTenantId(tenantId));
    }
}
