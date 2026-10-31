package com.ratelimiter.core.algorithm;

import com.ratelimiter.common.algorithm.AlgorithmType;
import com.ratelimiter.common.dto.RuleDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlgorithmRegistryTest {

    private static class Stub implements RateLimitAlgorithm {
        private final AlgorithmType type;

        Stub(AlgorithmType type) {
            this.type = type;
        }

        @Override
        public AlgorithmType type() {
            return type;
        }

        @Override
        public Mono<Decision> check(UUID tenantId, RuleDto rule) {
            return Mono.empty();
        }
    }

    private static RuleDto ruleWith(AlgorithmType type) {
        return new RuleDto(UUID.randomUUID(), UUID.randomUUID(), "*", "*", 5, 60, type, null, null);
    }

    @Test
    void resolvesTheImplementationForTheRulesType() {
        Stub fixedWindow = new Stub(AlgorithmType.FIXED_WINDOW);
        AlgorithmRegistry registry = new AlgorithmRegistry(List.of(fixedWindow));

        assertThat(registry.resolve(ruleWith(AlgorithmType.FIXED_WINDOW))).isSameAs(fixedWindow);
    }

    @Test
    void nullAlgorithmGivesTheDefault() {
        Stub fixedWindow = new Stub(AlgorithmType.FIXED_WINDOW);
        AlgorithmRegistry registry = new AlgorithmRegistry(List.of(fixedWindow));

        assertThat(registry.resolve(ruleWith(null))).isSameAs(fixedWindow);
    }

    @Test
    void startupFailsWhenTwoImplementationsClaimTheSameType() {
        assertThatThrownBy(() -> new AlgorithmRegistry(List.of(
                new Stub(AlgorithmType.FIXED_WINDOW), new Stub(AlgorithmType.FIXED_WINDOW))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIXED_WINDOW");
    }

    @Test
    void startupFailsWhenTheDefaultAlgorithmIsMissing() {
        assertThatThrownBy(() -> new AlgorithmRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default");
    }
}
