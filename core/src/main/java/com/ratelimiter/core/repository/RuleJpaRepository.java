package com.ratelimiter.core.repository;

import com.ratelimiter.common.entity.Rule;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Blocking Spring Data queries, used only by {@link RuleReadRepository}. Read methods only. */
interface RuleJpaRepository extends Repository<Rule, UUID> {

    List<Rule> findByTenantId(UUID tenantId);
}
