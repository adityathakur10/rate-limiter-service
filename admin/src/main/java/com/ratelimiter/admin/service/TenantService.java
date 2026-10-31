package com.ratelimiter.admin.service;

import com.ratelimiter.admin.entity.Tenant;
import com.ratelimiter.admin.exception.ResourceNotFoundException;
import com.ratelimiter.admin.mapper.TenantMapper;
import com.ratelimiter.admin.repository.TenantRepository;
import com.ratelimiter.admin.utils.ApiKeyGenerator;
import com.ratelimiter.common.dto.TenantDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, ApiKeyGenerator apiKeyGenerator,
                         TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.tenantMapper = tenantMapper;
    }

    @Transactional
    public TenantDto create(TenantDto request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setApiKey(apiKeyGenerator.generate());

        return tenantMapper.toDto(
            tenantRepository.save(tenant)
        );
    }

    @Transactional(readOnly = true)
    public TenantDto getById(UUID id) {
        return tenantRepository.findById(id)
                .map(tenantMapper::toDto)
                .orElseThrow(() -> 
                        new ResourceNotFoundException("Tenant not found: " + id));
    }
}
