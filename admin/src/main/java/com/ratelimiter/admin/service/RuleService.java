package com.ratelimiter.admin.service;

import com.ratelimiter.admin.entity.Rule;
import com.ratelimiter.admin.exception.ConflictException;
import com.ratelimiter.admin.exception.ResourceNotFoundException;
import com.ratelimiter.admin.mapper.RuleMapper;
import com.ratelimiter.admin.repository.RuleRepository;
import com.ratelimiter.admin.repository.TenantRepository;
import com.ratelimiter.admin.service.cache.CacheInvalidator;
import com.ratelimiter.common.dto.RuleDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RuleService {

    private final RuleRepository ruleRepository;
    private final TenantRepository tenantRepository;
    private final RuleMapper ruleMapper;
    private final CacheInvalidator cacheInvalidator;

    public RuleService(RuleRepository ruleRepository, TenantRepository tenantRepository,
                       RuleMapper ruleMapper, CacheInvalidator cacheInvalidator) {
        this.ruleRepository = ruleRepository;
        this.tenantRepository = tenantRepository;
        this.ruleMapper = ruleMapper;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public RuleDto create(RuleDto request) {
        requireTenant(request.tenantId());
        ruleRepository.findByTenantIdAndEndpointAndMethod(
                request.tenantId(), request.endpoint(), request.method())
                .ifPresent(existing -> {
                    throw duplicate(request);
                });

        Rule rule = new Rule();
        rule.setTenantId(request.tenantId());
        apply(rule, request);
        Rule saved = ruleRepository.save(rule);
        cacheInvalidator.invalidateRules(saved.getTenantId());
        return ruleMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleDto> listByTenant(UUID tenantId) {
        requireTenant(tenantId);
        return ruleRepository.findByTenantId(tenantId).stream()
                .map(ruleMapper::toDto)
                .toList();
    }

    @Transactional
    public RuleDto update(UUID id, RuleDto request) {
        Rule rule = findRule(id);
        // a rule never moves between tenants, so the request's tenantId is ignored
        ruleRepository.findByTenantIdAndEndpointAndMethod(
                rule.getTenantId(), request.endpoint(), request.method())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw duplicate(request);
                });

        apply(rule, request);
        // flush so @PreUpdate has set updatedAt before the DTO is built
        Rule saved = ruleRepository.saveAndFlush(rule);
        cacheInvalidator.invalidateRules(saved.getTenantId());
        return ruleMapper.toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Rule rule = findRule(id);
        ruleRepository.delete(rule);
        cacheInvalidator.invalidateRules(rule.getTenantId());
    }

    private void apply(Rule rule, RuleDto request) {
        rule.setEndpoint(request.endpoint());
        rule.setMethod(request.method());
        rule.setLimit(request.limit());
        rule.setWindowSeconds(request.windowSeconds());
    }

    private Rule findRule(UUID id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + id));
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private ConflictException duplicate(RuleDto request) {
        return new ConflictException("Rule already exists for endpoint %s and method %s"
                .formatted(request.endpoint(), request.method()));
    }
}
