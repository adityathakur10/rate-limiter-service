package com.ratelimiter.core.service;

import com.ratelimiter.common.entity.Tenant;
import com.ratelimiter.common.exception.UnauthorizedException;
import com.ratelimiter.core.CoreTestApplication;
import com.ratelimiter.core.RedisContainerConfig;
import com.ratelimiter.core.repository.TenantReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// real Redis, mocked Postgres: each test uses its own api key, so they do not see each other's cache
@SpringBootTest(classes = CoreTestApplication.class)
@Import(RedisContainerConfig.class)
class ApiKeyValidatorTest {

    @Autowired
    private ApiKeyValidator validator;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @MockitoBean
    private TenantReadRepository tenants;

    private static String newApiKey() {
        return "rl_" + UUID.randomUUID();
    }

    private static Tenant tenant(UUID id, String apiKey) {
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", id);
        tenant.setApiKey(apiKey);
        return tenant;
    }

    private void validate(UUID tenantId, String apiKey) {
        validator.validate(tenantId, apiKey).block(Duration.ofSeconds(10));
    }

    @Test
    void aMissLoadsTheTenantAndCachesItForTenMinutes() {
        UUID tenantId = UUID.randomUUID();
        String apiKey = newApiKey();
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.just(tenant(tenantId, apiKey)));

        validate(tenantId, apiKey);

        String cached = redis.opsForValue().get("tenant:" + apiKey).block();
        assertThat(cached).contains(tenantId.toString());
        Duration ttl = redis.getExpire("tenant:" + apiKey).block();
        assertThat(ttl).isBetween(Duration.ofSeconds(590), Duration.ofMinutes(10));
    }

    @Test
    void aHitDoesNotTouchPostgres() {
        UUID tenantId = UUID.randomUUID();
        String apiKey = newApiKey();
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.just(tenant(tenantId, apiKey)));

        validate(tenantId, apiKey);
        validate(tenantId, apiKey);
        validate(tenantId, apiKey);

        verify(tenants, times(1)).findByApiKey(apiKey);
    }

    @Test
    void anUnknownKeyIsUnauthorizedAndNothingIsCached() {
        String apiKey = newApiKey();
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> validate(UUID.randomUUID(), apiKey))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(redis.hasKey("tenant:" + apiKey).block()).isFalse();
    }

    @Test
    void aKeyOfAnotherTenantIsUnauthorizedOnAMissAndOnAHit() {
        UUID owner = UUID.randomUUID();
        String apiKey = newApiKey();
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.just(tenant(owner, apiKey)));

        // first call: miss, goes to Postgres; second call: served from the cache
        assertThatThrownBy(() -> validate(UUID.randomUUID(), apiKey)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> validate(UUID.randomUUID(), apiKey)).isInstanceOf(UnauthorizedException.class);

        validate(owner, apiKey);
    }

    @Test
    void anUnreadableCachedValueIsTreatedAsAMissAndOverwritten() {
        UUID tenantId = UUID.randomUUID();
        String apiKey = newApiKey();
        redis.opsForValue().set("tenant:" + apiKey, "not json").block();
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.just(tenant(tenantId, apiKey)));

        validate(tenantId, apiKey);

        verify(tenants, times(1)).findByApiKey(apiKey);
        assertThat(redis.opsForValue().get("tenant:" + apiKey).block()).contains(tenantId.toString());
    }
}
