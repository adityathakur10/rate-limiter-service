package com.ratelimiter.admin.mapper;

import com.ratelimiter.admin.entity.Rule;
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
            null, // algorithm column arrives in step 3.1; null means fixed_window
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }
}
