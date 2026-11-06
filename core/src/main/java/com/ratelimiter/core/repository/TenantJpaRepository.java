package com.ratelimiter.core.repository;

import com.ratelimiter.common.entity.Tenant;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Blocking Spring Data queries, used only by {@link TenantReadRepository}, which moves them off
 * the event loop. It extends the empty {@code Repository}, not {@code JpaRepository}, so core has
 * no save or delete methods at all.
 */
interface TenantJpaRepository extends Repository<Tenant, UUID> {

    Optional<Tenant> findByApiKey(String apiKey);
}
