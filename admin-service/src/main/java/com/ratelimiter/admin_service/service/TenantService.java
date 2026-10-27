package com.ratelimiter.admin_service.service;

import com.ratelimiter.admin_service.entity.Tenant;
import com.ratelimiter.admin_service.exception.ResourceNotFoundException;
import com.ratelimiter.admin_service.mapper.TenantMapper;
import com.ratelimiter.admin_service.repository.TenantRepository;
import com.ratelimiter.admin_service.utils.ApiKeyGenerator;
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
