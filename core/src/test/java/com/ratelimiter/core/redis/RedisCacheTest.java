package com.ratelimiter.core.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Redis is mocked so it can "fail"; the working path against a real Redis is covered by the
// validator and resolver tests
@ExtendWith(MockitoExtension.class)
class RedisCacheTest {

    @Mock
    private ReactiveStringRedisTemplate redis;
    @Mock
    private ReactiveValueOperations<String, String> values;

    private RedisCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisCache(redis);
    }

    @Test
    void getReturnsTheStoredText() {
        when(values.get("k")).thenReturn(Mono.just("v"));

        assertThat(cache.get("k").block()).isEqualTo("v");
    }

    @Test
    void getOfAMissingKeyIsEmpty() {
        when(values.get("k")).thenReturn(Mono.empty());

        assertThat(cache.get("k").blockOptional()).isEmpty();
    }

    @Test
    void aRedisFailureOnGetIsAMissNotAnError() {
        when(values.get("k")).thenReturn(Mono.error(new RedisConnectionFailureException("down")));

        assertThat(cache.get("k").blockOptional()).isEmpty();
    }

    @Test
    void setReportsWhetherItStored() {
        when(values.set("k", "v", Duration.ofMinutes(1))).thenReturn(Mono.just(true));

        assertThat(cache.set("k", "v", Duration.ofMinutes(1)).block()).isTrue();
    }

    @Test
    void aRedisFailureOnSetReturnsFalseNotAnError() {
        when(values.set("k", "v", Duration.ofMinutes(1)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("down")));

        assertThat(cache.set("k", "v", Duration.ofMinutes(1)).block()).isFalse();
    }
}
