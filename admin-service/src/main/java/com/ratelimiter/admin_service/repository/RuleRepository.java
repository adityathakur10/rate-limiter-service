package com.ratelimiter.admin_service.repository;

import com.ratelimiter.admin_service.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByTenantId(UUID tenantId);

    Optional<Rule> findByTenantIdAndEndpointAndMethod(UUID tenantId, String endpoint, String method);
}
