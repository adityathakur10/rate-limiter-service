package com.ratelimiter.core.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.core.CoreTestApplication;
import com.ratelimiter.core.RedisContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fails the build if someone adds an AlgorithmType value and forgets the implementation. */
@SpringBootTest(classes = CoreTestApplication.class)
@Import(RedisContainerConfig.class)
class AlgorithmCoverageTest {

    @Autowired
    private AlgorithmRegistry registry;

    @Test
    void everyAlgorithmTypeHasItsOwnImplementation() {
        Arrays.stream(AlgorithmType.values()).forEach(type -> {
            RuleDto rule = new RuleDto(UUID.randomUUID(), UUID.randomUUID(), "*", "*", 5, 60, type, null, null);
            // resolving to a different type would mean the registry fell back to the default
            assertThat(registry.resolve(rule).type()).as("implementation for %s", type).isEqualTo(type);
        });
    }
}
