package com.ratelimiter.core.controller;

import com.ratelimiter.core.CoreTestApplication;
import com.ratelimiter.core.RedisContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// the temporary rule from step 2.9 applies: 5 requests per 60 seconds for every tenant
@SpringBootTest(classes = CoreTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(RedisContainerConfig.class)
class RateLimitControllerTest {

    private static final String CHECK = "/v1/check";

    @Autowired
    private WebTestClient client;

    private Map<String, Object> body(UUID tenantId) {
        return Map.of("tenantId", tenantId, "apiKey", "any-key", "endpoint", "/payments/send", "method", "POST");
    }

    private WebTestClient.ResponseSpec post(Object body) {
        return client.post().uri(CHECK).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    @Test
    void allowedCallReturns200WithHeadersAndBody() {
        post(body(UUID.randomUUID()))
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "4")
                .expectHeader().exists("X-RateLimit-Reset")
                .expectHeader().doesNotExist("Retry-After")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(true)
                .jsonPath("$.remaining").isEqualTo(4)
                .jsonPath("$.resetAt").isNotEmpty()
                .jsonPath("$.retryAfterSeconds").doesNotExist();
    }

    @Test
    void resetHeaderIsAFutureEpochSecond() {
        String reset = post(body(UUID.randomUUID()))
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-RateLimit-Reset");

        assertThat(Long.parseLong(reset)).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void remainingFallsWithEachCallThenTheSixthIsDeniedWith429() {
        UUID tenant = UUID.randomUUID();

        for (int expectedRemaining = 4; expectedRemaining >= 0; expectedRemaining--) {
            post(body(tenant))
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-RateLimit-Remaining", String.valueOf(expectedRemaining));
        }

        String retryAfter = post(body(tenant))
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Limit", "5")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectHeader().exists("X-RateLimit-Reset")
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.allowed").isEqualTo(false)
                .jsonPath("$.remaining").isEqualTo(0)
                .jsonPath("$.retryAfterSeconds").isNumber()
                .jsonPath("$.resetAt").doesNotExist()
                .returnResult().getResponseHeaders().getFirst("Retry-After");

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    @Test
    void retryAfterHeaderMatchesTheBody() {
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            post(body(tenant)).expectStatus().isOk();
        }

        post(body(tenant))
                .expectStatus().isEqualTo(429)
                .expectBody()
                .consumeWith(result -> {
                    String header = result.getResponseHeaders().getFirst("Retry-After");
                    assertThat(new String(result.getResponseBody())).contains("\"retryAfterSeconds\":" + header);
                });
    }

    @Test
    void tenantsAreCountedSeparately() {
        UUID busy = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            post(body(busy));
        }

        post(body(busy)).expectStatus().isEqualTo(429);
        post(body(UUID.randomUUID())).expectStatus().isOk();
    }

    // bad input

    @Test
    void missingFieldsGive400() {
        post(Map.of("tenantId", UUID.randomUUID(), "apiKey", "k", "endpoint", "", "method", "POST"))
                .expectStatus().isBadRequest();
        post(Map.of("apiKey", "k", "endpoint", "/x", "method", "POST"))
                .expectStatus().isBadRequest();
    }

    @Test
    void tenantIdThatIsNotAUuidGives400() {
        post(Map.of("tenantId", "not-a-uuid", "apiKey", "k", "endpoint", "/x", "method", "POST"))
                .expectStatus().isBadRequest();
    }

    @Test
    void malformedJsonGives400() {
        post("{ this is not json")
                .expectStatus().isBadRequest();
    }

    @Test
    void emptyBodyGives400() {
        client.post().uri(CHECK).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isBadRequest();
    }
}
