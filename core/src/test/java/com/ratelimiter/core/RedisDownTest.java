package com.ratelimiter.core;

import com.ratelimiter.common.entity.Rule;
import com.ratelimiter.common.entity.Tenant;
import com.ratelimiter.common.exception.UnauthorizedException;
import com.ratelimiter.core.repository.RuleReadRepository;
import com.ratelimiter.core.repository.TenantReadRepository;
import com.ratelimiter.core.service.ApiKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Redis is really unreachable here (nothing listens on port 1), Postgres is mocked
@SpringBootTest(classes = CoreTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.redis.port=1",
                "spring.data.redis.timeout=500ms",
                "spring.data.redis.connect-timeout=500ms"})
@AutoConfigureWebTestClient
class RedisDownTest {

    private final UUID tenantId = UUID.randomUUID();
    private final String apiKey = "rl_" + UUID.randomUUID();

    @Autowired
    private WebTestClient client;
    @Autowired
    private ApiKeyValidator validator;

    @MockitoBean
    private TenantReadRepository tenants;
    @MockitoBean
    private RuleReadRepository rules;

    @BeforeEach
    void stubPostgres() {
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        tenant.setApiKey(apiKey);
        when(tenants.findByApiKey(apiKey)).thenReturn(Mono.just(tenant));
        when(tenants.findByApiKey("rl_unknown")).thenReturn(Mono.empty());

        // a limit of 1: with a working Redis the second call would already be denied
        Rule rule = new Rule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setTenantId(tenantId);
        rule.setEndpoint("/pay");
        rule.setMethod("POST");
        rule.setLimit(1);
        rule.setWindowSeconds(60);
        when(rules.findByTenantId(tenantId)).thenReturn(Mono.just(List.of(rule)));
    }

    private WebTestClient.ResponseSpec check() {
        return client.mutate().responseTimeout(Duration.ofSeconds(15)).build()
                .post().uri("/v1/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tenantId", tenantId, "apiKey", apiKey, "endpoint", "/pay", "method", "POST"))
                .exchange();
    }

    @Test
    void checksAreAllowedWhileRedisIsDown() {
        for (int i = 0; i < 3; i++) {
            check().expectStatus().isOk()
                    .expectHeader().doesNotExist("X-RateLimit-Limit")
                    .expectBody()
                    .jsonPath("$.allowed").isEqualTo(true);
        }
    }

    @Test
    void theKeyIsStillCheckedAgainstPostgresWhileRedisIsDown() {
        validator.validate(tenantId, apiKey).block(Duration.ofSeconds(15));

        assertThatThrownBy(() -> validator.validate(tenantId, "rl_unknown").block(Duration.ofSeconds(15)))
                .isInstanceOf(UnauthorizedException.class);
    }
}
