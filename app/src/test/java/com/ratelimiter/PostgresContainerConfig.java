package com.ratelimiter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// one Postgres for every test that imports this; same image as docker-compose
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:16-bookworm");
	}

}
