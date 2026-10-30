package com.ratelimiter.admin_service.mapper;

import com.ratelimiter.admin_service.entity.Rule;
import com.ratelimiter.admin_service.entity.Tenant;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.dto.TenantDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    @Test
    void tenantMapperCopiesEveryField() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-15T10:00:00Z");
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", id);
        ReflectionTestUtils.setField(tenant, "createdAt", createdAt);
        tenant.setName("acme");
        tenant.setApiKey("rl_abc");

        TenantDto dto = new TenantMapper().toDto(tenant);

        assertThat(dto).isEqualTo(new TenantDto(id, "acme", "rl_abc", createdAt));
    }

    @Test
    void ruleMapperCopiesEveryFieldAndMapsLimit() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-15T11:00:00Z");
        Rule rule = new Rule();
        ReflectionTestUtils.setField(rule, "id", id);
        ReflectionTestUtils.setField(rule, "createdAt", createdAt);
        ReflectionTestUtils.setField(rule, "updatedAt", updatedAt);
        rule.setTenantId(tenantId);
        rule.setEndpoint("/pay");
        rule.setMethod("POST");
        rule.setLimit(5);
        rule.setWindowSeconds(60);

        RuleDto dto = new RuleMapper().toDto(rule);

        assertThat(dto).isEqualTo(new RuleDto(id, tenantId, "/pay", "POST", 5, 60, createdAt, updatedAt));
    }
}
