package com.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;

// boots the whole app against a real Postgres: covers Flyway and ddl-auto=validate.
// Annotated like the integration tests so they all share one cached context and one container.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(PostgresContainerConfig.class)
class RateLimiterApplicationTests {

	@Test
	void contextLoads() {
	}

}
