package com.ratelimiter.admin_service.mapper;

import com.ratelimiter.admin_service.entity.Rule;
import com.ratelimiter.common.dto.RuleDto;
import org.springframework.stereotype.Component;

@Component
public class RuleMapper {

    public RuleDto toDto(Rule rule) {
        return new RuleDto(
            rule.getId(),
            rule.getTenantId(),
            rule.getEndpoint(),
            rule.getMethod(),
            rule.getLimit(),
            rule.getWindowSeconds(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }
}
