package com.ratelimiter;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.TenantDto;
import com.ratelimiter.common.entity.Rule;
import com.ratelimiter.common.entity.Tenant;
import com.ratelimiter.core.repository.RuleReadRepository;
import com.ratelimiter.core.repository.TenantReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// data is created through the admin API, then read back through core's repositories,
// so this also checks that core's reads match the schema admin's Flyway scripts build
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
class ReadRepositoriesIntegrationTest {

	@Autowired
	private WebTestClient client;
	@Autowired
	private TenantReadRepository tenants;
	@Autowired
	private RuleReadRepository rules;

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

	private void createRule(UUID tenantId, String endpoint, String method, int limit, int window) {
		client.post().uri("/admin/v1/rules")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("tenantId", tenantId, "endpoint", endpoint, "method", method,
						"limit", limit, "windowSeconds", window))
				.exchange()
				.expectStatus().isCreated();
	}

	@Test
	void findsATenantByItsApiKey() {
		TenantDto created = createTenant("by-key");

		Tenant found = tenants.findByApiKey(created.apiKey()).block();

		assertThat(found).isNotNull();
		assertThat(found.getId()).isEqualTo(created.id());
		assertThat(found.getApiKey()).isEqualTo(created.apiKey());
	}

	@Test
	void anUnknownApiKeyGivesAnEmptyMono() {
		assertThat(tenants.findByApiKey("rl_does_not_exist").blockOptional()).isEmpty();
	}

	@Test
	void findsOnlyTheRulesOfTheRequestedTenant() {
		TenantDto first = createTenant("rules-first");
		TenantDto second = createTenant("rules-second");
		createRule(first.id(), "/pay", "POST", 5, 60);
		createRule(second.id(), "/a", "GET", 10, 30);
		createRule(second.id(), "*", "*", 100, 60);

		List<Rule> found = rules.findByTenantId(second.id()).block();

		assertThat(found).hasSize(2);
		assertThat(found).allSatisfy(rule -> assertThat(rule.getTenantId()).isEqualTo(second.id()));
		assertThat(found).anySatisfy(rule -> {
			assertThat(rule.getEndpoint()).isEqualTo("/a");
			assertThat(rule.getMethod()).isEqualTo("GET");
			assertThat(rule.getLimit()).isEqualTo(10);
			assertThat(rule.getWindowSeconds()).isEqualTo(30);
			assertThat(rule.getAlgorithm()).isEqualTo(AlgorithmType.FIXED_WINDOW);
		});
	}

	@Test
	void aTenantWithoutRulesGivesAnEmptyList() {
		TenantDto tenant = createTenant("no-rules");

		assertThat(rules.findByTenantId(tenant.id()).block()).isEmpty();
	}

	@Test
	void queriesRunOnTheBoundedElasticPool() {
		TenantDto tenant = createTenant("thread-check");

		// map runs on the thread that emitted the result, which is the one that ran the query
		String thread = tenants.findByApiKey(tenant.apiKey())
				.map(found -> Thread.currentThread().getName())
				.block();

		assertThat(thread).startsWith("boundedElastic");
	}
}
