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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(PostgresContainerConfig.class)
class AdminApiIntegrationTest {

	private static final String TENANTS = "/admin/v1/tenants";
	private static final String RULES = "/admin/v1/rules";

	@Autowired
	private WebTestClient client;

	// helpers

	private TenantDto createTenant(String name) {
		TenantDto tenant = client.post().uri(TENANTS)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("name", name))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(TenantDto.class).returnResult().getResponseBody();
		assertThat(tenant).isNotNull();
		return tenant;
	}

	private Map<String, Object> ruleBody(UUID tenantId, String endpoint, String method, int limit, int window) {
		return Map.of("tenantId", tenantId, "endpoint", endpoint, "method", method,
				"limit", limit, "windowSeconds", window);
	}

	private WebTestClient.ResponseSpec postRule(Map<String, Object> body) {
		return client.post().uri(RULES).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
	}

	private RuleDto createRule(UUID tenantId, String endpoint, String method, int limit, int window) {
		RuleDto rule = postRule(ruleBody(tenantId, endpoint, method, limit, window))
				.expectStatus().isCreated()
				.expectBody(RuleDto.class).returnResult().getResponseBody();
		assertThat(rule).isNotNull();
		return rule;
	}

	// tenants

	@Test
	void createTenantReturns201WithAGeneratedKey() {
		client.post().uri(TENANTS)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("name", "acme"))
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isNotEmpty()
				.jsonPath("$.name").isEqualTo("acme")
				.jsonPath("$.apiKey").value(key -> assertThat((String) key).startsWith("rl_"))
				.jsonPath("$.createdAt").isNotEmpty();
	}

	@Test
	void twoTenantsGetDifferentKeys() {
		assertThat(createTenant("one").apiKey()).isNotEqualTo(createTenant("two").apiKey());
	}

	@Test
	void createTenantWithBlankNameReturns400ListingTheField() {
		client.post().uri(TENANTS)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("name", " "))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.message").isEqualTo("Validation failed")
				.jsonPath("$.fields.name").isNotEmpty();
	}

	@Test
	void getTenantReturnsItAndUnknownIdReturns404() {
		TenantDto tenant = createTenant("lookup");

		client.get().uri(TENANTS + "/{id}", tenant.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody(TenantDto.class).isEqualTo(tenant);

		client.get().uri(TENANTS + "/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.status").isEqualTo(404)
				.jsonPath("$.message").value(message -> assertThat((String) message).contains("Tenant not found"));
	}

	@Test
	void getTenantWithANonUuidIdReturns400() {
		client.get().uri(TENANTS + "/not-a-uuid")
				.exchange()
				.expectStatus().isBadRequest();
	}

	// rules: full CRUD

	@Test
	void ruleCrudLifecycle() {
		TenantDto tenant = createTenant("crud");

		RuleDto created = createRule(tenant.id(), "/payments/send", "POST", 5, 60);
		assertThat(created.id()).isNotNull();
		assertThat(created.tenantId()).isEqualTo(tenant.id());
		assertThat(created.limit()).isEqualTo(5);
		assertThat(created.windowSeconds()).isEqualTo(60);
		assertThat(created.createdAt()).isNotNull();

		client.get().uri(RULES + "?tenantId={id}", tenant.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(1)
				.jsonPath("$[0].id").isEqualTo(created.id().toString())
				.jsonPath("$[0].limit").isEqualTo(5);

		client.put().uri(RULES + "/{id}", created.id())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(tenant.id(), "/payments/send", "POST", 10, 30))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(created.id().toString())
				.jsonPath("$.limit").isEqualTo(10)
				.jsonPath("$.windowSeconds").isEqualTo(30);

		client.delete().uri(RULES + "/{id}", created.id())
				.exchange()
				.expectStatus().isNoContent();

		client.get().uri(RULES + "?tenantId={id}", tenant.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(0);
	}

	@Test
	void listOnlyReturnsTheRequestedTenantsRules() {
		TenantDto first = createTenant("first");
		TenantDto second = createTenant("second");
		createRule(first.id(), "/a", "GET", 1, 10);
		createRule(second.id(), "/b", "GET", 1, 10);
		createRule(second.id(), "/c", "GET", 1, 10);

		client.get().uri(RULES + "?tenantId={id}", second.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(2);
	}

	@Test
	void wildcardEndpointAndMethodAreStoredAsGiven() {
		TenantDto tenant = createTenant("wild");

		RuleDto rule = createRule(tenant.id(), "*", "*", 100, 60);

		assertThat(rule.endpoint()).isEqualTo("*");
		assertThat(rule.method()).isEqualTo("*");
	}

	// rules: error paths

	@Test
	void duplicateEndpointAndMethodReturns409() {
		TenantDto tenant = createTenant("dup");
		createRule(tenant.id(), "/pay", "POST", 5, 60);

		postRule(ruleBody(tenant.id(), "/pay", "POST", 9, 9))
				.expectStatus().isEqualTo(409)
				.expectBody()
				.jsonPath("$.status").isEqualTo(409)
				.jsonPath("$.error").isEqualTo("Conflict");
	}

	@Test
	void sameEndpointWithADifferentMethodIsNotADuplicate() {
		TenantDto tenant = createTenant("methods");
		createRule(tenant.id(), "/pay", "POST", 5, 60);

		createRule(tenant.id(), "/pay", "GET", 5, 60);
	}

	@Test
	void sameEndpointAndMethodIsAllowedForADifferentTenant() {
		createRule(createTenant("t1").id(), "/pay", "POST", 5, 60);

		createRule(createTenant("t2").id(), "/pay", "POST", 5, 60);
	}

	@Test
	void updatingIntoAnExistingEndpointAndMethodReturns409() {
		TenantDto tenant = createTenant("update-conflict");
		createRule(tenant.id(), "/a", "GET", 5, 60);
		RuleDto other = createRule(tenant.id(), "/b", "GET", 5, 60);

		client.put().uri(RULES + "/{id}", other.id())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(tenant.id(), "/a", "GET", 5, 60))
				.exchange()
				.expectStatus().isEqualTo(409);
	}

	@Test
	void createRuleForAnUnknownTenantReturns404() {
		postRule(ruleBody(UUID.randomUUID(), "/pay", "POST", 5, 60))
				.expectStatus().isNotFound();
	}

	@Test
	void listRulesForAnUnknownTenantReturns404() {
		client.get().uri(RULES + "?tenantId={id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void updateAndDeleteOfAnUnknownRuleReturn404() {
		UUID unknown = UUID.randomUUID();

		client.put().uri(RULES + "/{id}", unknown)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(ruleBody(UUID.randomUUID(), "/pay", "POST", 5, 60))
				.exchange()
				.expectStatus().isNotFound();

		client.delete().uri(RULES + "/{id}", unknown)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void deletingARuleTwiceReturns404TheSecondTime() {
		TenantDto tenant = createTenant("delete-twice");
		RuleDto rule = createRule(tenant.id(), "/pay", "POST", 5, 60);

		client.delete().uri(RULES + "/{id}", rule.id()).exchange().expectStatus().isNoContent();
		client.delete().uri(RULES + "/{id}", rule.id()).exchange().expectStatus().isNotFound();
	}

	@Test
	void invalidRuleBodyReturns400ListingEveryFailingField() {
		client.post().uri(RULES)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("endpoint", "", "method", "POST", "limit", 0, "windowSeconds", 0))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Validation failed")
				.jsonPath("$.fields.tenantId").isNotEmpty()
				.jsonPath("$.fields.endpoint").isNotEmpty()
				.jsonPath("$.fields.limit").isNotEmpty()
				.jsonPath("$.fields.windowSeconds").isNotEmpty();
	}

	@Test
	void malformedJsonReturns400() {
		client.post().uri(RULES)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{bad")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Malformed or missing request body");
	}

	@Test
	void missingBodyReturns400() {
		client.post().uri(RULES)
				.contentType(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void missingTenantIdParameterReturns400() {
		client.get().uri(RULES)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Missing required parameter 'tenantId'");
	}

	@Test
	void nonUuidTenantIdParameterReturns400() {
		client.get().uri(RULES + "?tenantId=abc")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.message").isEqualTo("Invalid value for parameter 'tenantId'");
	}

}
