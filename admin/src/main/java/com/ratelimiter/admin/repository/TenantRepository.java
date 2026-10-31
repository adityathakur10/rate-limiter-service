package com.ratelimiter.admin.repository;

import com.ratelimiter.admin.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByApiKey(String apiKey);
}
