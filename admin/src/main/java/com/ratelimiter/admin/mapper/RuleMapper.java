package com.ratelimiter.admin.mapper;

import com.ratelimiter.common.entity.Rule;
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
            rule.getAlgorithm(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }
}
