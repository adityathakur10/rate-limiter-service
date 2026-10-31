package com.ratelimiter.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Test-only application for the core module. It scans only the core package and leaves out the
 * database auto-configuration, so these tests need Redis and nothing else. The real
 * application (with the admin module and Postgres) lives in the app module.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ComponentScan(basePackages = "com.ratelimiter.core")
public class CoreTestApplication {
}
