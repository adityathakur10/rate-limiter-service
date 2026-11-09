package com.ratelimiter;

import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.dto.TenantDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// the whole app, real Postgres and real Redis: tenants and rules are created through the admin API,
// then /v1/check is called. Every test makes its own tenant and creates its rules before the first
// check, because a tenant's rules are cached for five minutes after that.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
class CheckFlowIntegrationTest {

	private static final String CHECK = "/v1/check";

	@Autowired
	private WebTestClient client;

	private TenantDto createTenant(String name) {
		TenantDto tenant = client.post().uri("/admin/v1/tenants")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("name", name))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(TenantDto.class).returnResult().getResponseBody();
		assertThat(tenant).isNotNull();
		return tenant;
	}

	private UUID createRule(UUID tenantId, String endpoint, String method, int limit, int window) {
		RuleDto rule = client.post().uri("/admin/v1/rules")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(tenantId, endpoint, method, limit, window))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(RuleDto.class).returnResult().getResponseBody();
		assertThat(rule).isNotNull();
		return rule.id();
	}

	private Map<String, Object> ruleBody(UUID tenantId, String endpoint, String method, int limit, int window) {
		return Map.of("tenantId", tenantId, "endpoint", endpoint, "method", method,
				"limit", limit, "windowSeconds", window);
	}

	private WebTestClient.ResponseSpec check(UUID tenantId, String apiKey, String endpoint, String method) {
		return client.post().uri(CHECK)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("tenantId", tenantId, "apiKey", apiKey, "endpoint", endpoint, "method", method))
				.exchange();
	}

	@Test
	void aTenantsOwnRuleIsEnforcedUntilTheLimitThenDenied() {
		TenantDto tenant = createTenant("limited");
		createRule(tenant.id(), "/pay", "POST", 3, 60);

		for (int remaining = 2; remaining >= 0; remaining--) {
			check(tenant.id(), tenant.apiKey(), "/pay", "POST")
					.expectStatus().isOk()
					.expectHeader().valueEquals("X-RateLimit-Limit", "3")
					.expectHeader().valueEquals("X-RateLimit-Remaining", String.valueOf(remaining));
		}

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isEqualTo(429)
				.expectHeader().exists("Retry-After")
				.expectBody()
				.jsonPath("$.allowed").isEqualTo(false);
	}

	@Test
	void aWrongApiKeyIs401() {
		TenantDto tenant = createTenant("wrong-key");
		createRule(tenant.id(), "/pay", "POST", 3, 60);

		check(tenant.id(), "rl_not_a_real_key", "/pay", "POST")
				.expectStatus().isUnauthorized()
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.error").isEqualTo("Unauthorized")
				.jsonPath("$.message").isEqualTo("Invalid API key");
	}

	@Test
	void anotherTenantsKeyIs401() {
		TenantDto victim = createTenant("victim");
		TenantDto other = createTenant("other");

		check(victim.id(), other.apiKey(), "/pay", "POST")
				.expectStatus().isUnauthorized();
	}

	@Test
	void anUnknownTenantIs401NotAnInformativeError() {
		TenantDto tenant = createTenant("real");

		check(UUID.randomUUID(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isUnauthorized();
	}

	@Test
	void aRequestNoRuleAppliesToIsAllowedWithoutLimitHeaders() {
		TenantDto tenant = createTenant("no-match");
		createRule(tenant.id(), "/pay", "POST", 1, 60);

		check(tenant.id(), tenant.apiKey(), "/orders", "GET")
				.expectStatus().isOk()
				.expectHeader().doesNotExist("X-RateLimit-Limit")
				.expectBody()
				.jsonPath("$.allowed").isEqualTo(true);
	}

	@Test
	void aTenantWithoutAnyRuleIsAllowed() {
		TenantDto tenant = createTenant("no-rules");

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk();
	}

	@Test
	void theMostSpecificRuleWinsAndAWildcardRuleCoversTheRest() {
		TenantDto tenant = createTenant("specific");
		createRule(tenant.id(), "*", "*", 100, 60);
		createRule(tenant.id(), "/pay", "POST", 2, 60);

		// the exact rule: limit 2, so the third call is denied
		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Limit", "2");
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isOk();
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isEqualTo(429);

		// anything else falls to the wildcard rule
		check(tenant.id(), tenant.apiKey(), "/orders", "GET")
				.expectStatus().isOk()
				.expectHeader().valueEquals("X-RateLimit-Limit", "100");
	}

	// rule changes: the cached copy is deleted after the commit, so the next check sees the change at once

	@Test
	void editingARuleTakesEffectOnTheNextCheck() {
		TenantDto tenant = createTenant("edit");
		UUID ruleId = createRule(tenant.id(), "/pay", "POST", 2, 60);
		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Limit", "2");   // rules are now cached

		client.put().uri("/admin/v1/rules/{id}", ruleId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(tenant.id(), "/pay", "POST", 5, 60))
				.exchange()
				.expectStatus().isOk();

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Limit", "5");
	}

	@Test
	void aRuleCreatedAfterTheFirstCheckTakesEffectOnTheNextCheck() {
		TenantDto tenant = createTenant("late-rule");
		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().doesNotExist("X-RateLimit-Limit");   // no rules, "[]" is cached

		createRule(tenant.id(), "/pay", "POST", 1, 60);

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Limit", "1");
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isEqualTo(429);
	}

	@Test
	void aDeletedRuleStopsLimitingOnTheNextCheck() {
		TenantDto tenant = createTenant("deleted");
		UUID ruleId = createRule(tenant.id(), "/pay", "POST", 1, 60);
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isOk();
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isEqualTo(429);

		client.delete().uri("/admin/v1/rules/{id}", ruleId).exchange().expectStatus().isNoContent();

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk().expectHeader().doesNotExist("X-RateLimit-Limit");
	}

	@Test
	void aRejectedRuleWriteLeavesTheCachedRulesAlone() {
		TenantDto tenant = createTenant("rejected");
		createRule(tenant.id(), "/pay", "POST", 3, 60);
		check(tenant.id(), tenant.apiKey(), "/pay", "POST").expectStatus().isOk();

		// a duplicate is rejected with 409 and rolled back, so nothing changed and nothing is invalidated
		client.post().uri("/admin/v1/rules")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(tenant.id(), "/pay", "POST", 9, 9))
				.exchange()
				.expectStatus().isEqualTo(409);

		check(tenant.id(), tenant.apiKey(), "/pay", "POST")
				.expectStatus().isOk()
				.expectHeader().valueEquals("X-RateLimit-Limit", "3")
				.expectHeader().valueEquals("X-RateLimit-Remaining", "1");   // the same counter, still going
	}

	@Test
	void tenantsAreCountedSeparately() {
		TenantDto busy = createTenant("busy");
		TenantDto calm = createTenant("calm");
		createRule(busy.id(), "/pay", "POST", 1, 60);
		createRule(calm.id(), "/pay", "POST", 1, 60);

		check(busy.id(), busy.apiKey(), "/pay", "POST").expectStatus().isOk();
		check(busy.id(), busy.apiKey(), "/pay", "POST").expectStatus().isEqualTo(429);

		check(calm.id(), calm.apiKey(), "/pay", "POST").expectStatus().isOk();
	}
}
