package com.ratelimiter.admin.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void keyStartsWithPrefixAndIsUrlSafeBase64OfThirtyTwoBytes() {
        String key = generator.generate();

        assertThat(key).startsWith("rl_");
        String encoded = key.substring("rl_".length());
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(encoded)).hasSize(32);
    }

    @Test
    void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(generator.generate());
        }

        assertThat(keys).hasSize(1000);
    }
}
