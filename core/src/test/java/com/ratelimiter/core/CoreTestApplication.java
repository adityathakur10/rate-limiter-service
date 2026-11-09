package com.ratelimiter.core;

import com.ratelimiter.core.repository.RuleReadRepository;
import com.ratelimiter.core.repository.TenantReadRepository;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only application for the core module. It scans only the core package and leaves out the
 * database auto-configuration, so these tests need Redis and nothing else. For the same reason it
 * skips the repository package, whose beans need JPA, and offers mock read repositories in their
 * place. The real application (with the admin module and Postgres) lives in the app module, where
 * the repositories are tested.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ComponentScan(basePackages = "com.ratelimiter.core", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ratelimiter\\.core\\.repository\\..*"),
        // a hand-written scan, unlike @SpringBootApplication's, would pick up this test configuration
        // and start Redis for every test; the tests that want it @Import it
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RedisContainerConfig.class)
})
public class CoreTestApplication {

    @Bean
    TenantReadRepository tenantReadRepository() {
        return Mockito.mock(TenantReadRepository.class);
    }

    @Bean
    RuleReadRepository ruleReadRepository() {
        return Mockito.mock(RuleReadRepository.class);
    }
}
