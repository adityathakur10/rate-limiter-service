package com.ratelimiter.admin.service.cache;

import com.ratelimiter.common.cache.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Deletes {@code rules:{tenantId}} once the rule change has been committed. Deleting before the
 * commit would let core re-cache the old rules in between; if the transaction rolls back nothing
 * is deleted. Uses the blocking template: rule writes already run on the bounded-elastic pool.
 * A Redis failure never fails the write (it is already committed); the stale copy then expires
 * on its own TTL.
 */
@Component
public class RedisCacheInvalidator implements CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidator.class);

    private final StringRedisTemplate redis;

    public RedisCacheInvalidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void invalidateRules(UUID tenantId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(tenantId);
                }
            });
        } else {
            delete(tenantId);
        }
    }

    private void delete(UUID tenantId) {
        try {
            redis.delete(CacheKeys.rules(tenantId));
        } catch (RuntimeException e) {
            log.warn("Could not delete the cached rules of tenant {}; the stale copy expires on its own: {}",
                    tenantId, e.toString());
        }
    }
}
