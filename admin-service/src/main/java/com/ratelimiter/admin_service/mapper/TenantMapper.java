package com.ratelimiter.admin_service.mapper;

import com.ratelimiter.admin_service.entity.Tenant;
import com.ratelimiter.common.dto.TenantDto;
import org.springframework.stereotype.Component;

@Component
public class TenantMapper {

    public TenantDto toDto(Tenant tenant) {
        return new TenantDto(
            tenant.getId(), 
            tenant.getName(), 
            tenant.getApiKey(), 
            tenant.getCreatedAt()
        );
    }
}
