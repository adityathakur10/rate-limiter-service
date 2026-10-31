package com.ratelimiter.admin_service.service;

import com.ratelimiter.admin_service.entity.Rule;
import com.ratelimiter.admin_service.exception.ConflictException;
import com.ratelimiter.admin_service.exception.ResourceNotFoundException;
import com.ratelimiter.admin_service.mapper.RuleMapper;
import com.ratelimiter.admin_service.repository.RuleRepository;
import com.ratelimiter.admin_service.repository.TenantRepository;
import com.ratelimiter.admin_service.service.cache.CacheInvalidator;
import com.ratelimiter.common.dto.RuleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CacheInvalidator cacheInvalidator;

    private RuleService ruleService;

    @BeforeEach
    void setUp() {
        ruleService = new RuleService(ruleRepository, tenantRepository, new RuleMapper(), cacheInvalidator);
    }

    private RuleDto request(String endpoint, String method, int limit, int windowSeconds) {
        return new RuleDto(null, tenantId, endpoint, method, limit, windowSeconds, null, null, null);
    }

    private Rule existingRule(UUID id, String endpoint, String method) {
        Rule rule = new Rule();
        ReflectionTestUtils.setField(rule, "id", id);
        rule.setTenantId(tenantId);
        rule.setEndpoint(endpoint);
        rule.setMethod(method);
        rule.setLimit(5);
        rule.setWindowSeconds(60);
        return rule;
    }

    // create

    @Test
    void createSavesTheRuleAndInvalidatesTheTenantCache() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(ruleRepository.findByTenantIdAndEndpointAndMethod(tenantId, "/pay", "POST"))
                .thenReturn(Optional.empty());
        when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDto result = ruleService.create(request("/pay", "POST", 5, 60));

        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.endpoint()).isEqualTo("/pay");
        assertThat(result.method()).isEqualTo("POST");
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.windowSeconds()).isEqualTo(60);
        verify(cacheInvalidator).invalidateRules(tenantId);
    }

    @Test
    void createRejectsAnUnknownTenant() {
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> ruleService.create(request("/pay", "POST", 5, 60)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepository, never()).save(any());
        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    void createRejectsADuplicateEndpointAndMethod() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(ruleRepository.findByTenantIdAndEndpointAndMethod(tenantId, "/pay", "POST"))
                .thenReturn(Optional.of(existingRule(UUID.randomUUID(), "/pay", "POST")));

        assertThatThrownBy(() -> ruleService.create(request("/pay", "POST", 5, 60)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("/pay")
                .hasMessageContaining("POST");

        verify(ruleRepository, never()).save(any());
        verifyNoInteractions(cacheInvalidator);
    }

    // list

    @Test
    void listReturnsTheTenantsRules() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(ruleRepository.findByTenantId(tenantId)).thenReturn(List.of(
                existingRule(UUID.randomUUID(), "/pay", "POST"),
                existingRule(UUID.randomUUID(), "*", "*")));

        List<RuleDto> result = ruleService.listByTenant(tenantId);

        assertThat(result).extracting(RuleDto::endpoint).containsExactly("/pay", "*");
    }

    @Test
    void listRejectsAnUnknownTenant() {
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> ruleService.listByTenant(tenantId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepository, never()).findByTenantId(any());
    }

    // update

    @Test
    void updateChangesTheFieldsAndInvalidatesTheCache() {
        UUID id = UUID.randomUUID();
        Rule rule = existingRule(id, "/pay", "POST");
        when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));
        // the only rule on this endpoint and method is the one being updated, so no conflict
        when(ruleRepository.findByTenantIdAndEndpointAndMethod(tenantId, "/pay", "POST"))
                .thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(rule)).thenReturn(rule);

        RuleDto result = ruleService.update(id, request("/pay", "POST", 10, 30));

        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.windowSeconds()).isEqualTo(30);
        verify(cacheInvalidator).invalidateRules(tenantId);
    }

    @Test
    void updateIgnoresTheTenantIdInTheRequest() {
        UUID id = UUID.randomUUID();
        Rule rule = existingRule(id, "/pay", "POST");
        UUID otherTenant = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));
        when(ruleRepository.findByTenantIdAndEndpointAndMethod(tenantId, "/pay", "POST"))
                .thenReturn(Optional.of(rule));
        when(ruleRepository.saveAndFlush(rule)).thenReturn(rule);

        RuleDto result = ruleService.update(id,
                new RuleDto(null, otherTenant, "/pay", "POST", 10, 30, null, null, null));

        assertThat(result.tenantId()).isEqualTo(tenantId);
        verify(cacheInvalidator).invalidateRules(tenantId);
    }

    @Test
    void updateRejectsAnotherRuleWithTheSameEndpointAndMethod() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.of(existingRule(id, "/pay", "POST")));
        when(ruleRepository.findByTenantIdAndEndpointAndMethod(tenantId, "/search", "GET"))
                .thenReturn(Optional.of(existingRule(UUID.randomUUID(), "/search", "GET")));

        assertThatThrownBy(() -> ruleService.update(id, request("/search", "GET", 5, 60)))
                .isInstanceOf(ConflictException.class);

        verify(ruleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cacheInvalidator);
    }

    @Test
    void updateThrowsNotFoundForAnUnknownRule() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.update(id, request("/pay", "POST", 5, 60)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());

        verifyNoInteractions(cacheInvalidator);
    }

    // delete

    @Test
    void deleteRemovesTheRuleAndInvalidatesTheCache() {
        UUID id = UUID.randomUUID();
        Rule rule = existingRule(id, "/pay", "POST");
        when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));

        ruleService.delete(id);

        verify(ruleRepository).delete(rule);
        verify(cacheInvalidator).invalidateRules(tenantId);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownRule() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepository, never()).delete(any());
        verifyNoInteractions(cacheInvalidator);
    }
}
