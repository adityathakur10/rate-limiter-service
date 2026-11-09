package com.ratelimiter.admin.service.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// the transaction is simulated by hand: initSynchronization() is what a running @Transactional does,
// and afterCommit() is what the transaction manager calls once the commit has succeeded
@ExtendWith(MockitoExtension.class)
class RedisCacheInvalidatorTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private StringRedisTemplate redis;

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void insideATransactionTheKeyIsDeletedOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        RedisCacheInvalidator invalidator = new RedisCacheInvalidator(redis);

        invalidator.invalidateRules(tenantId);
        verifyNoInteractions(redis);

        commit();
        verify(redis).delete("rules:" + tenantId);
    }

    @Test
    void aRolledBackTransactionDeletesNothing() {
        TransactionSynchronizationManager.initSynchronization();
        RedisCacheInvalidator invalidator = new RedisCacheInvalidator(redis);

        invalidator.invalidateRules(tenantId);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(redis);
    }

    @Test
    void withoutATransactionTheKeyIsDeletedAtOnce() {
        new RedisCacheInvalidator(redis).invalidateRules(tenantId);

        verify(redis).delete("rules:" + tenantId);
    }

    @Test
    void aRedisFailureDoesNotFailTheWrite() {
        when(redis.delete("rules:" + tenantId)).thenThrow(new RedisConnectionFailureException("down"));
        TransactionSynchronizationManager.initSynchronization();
        RedisCacheInvalidator invalidator = new RedisCacheInvalidator(redis);

        invalidator.invalidateRules(tenantId);

        commit();   // must not throw: the rule change is already committed
        verify(redis).delete("rules:" + tenantId);
    }
}
