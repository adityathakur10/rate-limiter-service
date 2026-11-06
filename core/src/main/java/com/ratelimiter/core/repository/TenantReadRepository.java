package com.ratelimiter.core.repository;

import com.ratelimiter.common.entity.Tenant;
import com.ratelimiter.common.reactive.BlockingCall;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Read-only tenant lookups for the hot path. Every query runs on the bounded-elastic pool. */
@Component
public class TenantReadRepository {

    private final TenantJpaRepository jpa;

    public TenantReadRepository(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** Empty when no tenant has this key. */
    public Mono<Tenant> findByApiKey(String apiKey) {
        return BlockingCall.call(() -> jpa.findByApiKey(apiKey).orElse(null));
    }
}
