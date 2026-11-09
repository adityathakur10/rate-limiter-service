package com.ratelimiter.core.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

// the working path against a real Redis is covered by FixedWindowLimiterTest
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class LuaScriptExecutorTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redis;
    @Mock
    private RedisScript<List<Long>> script;

    @Test
    void returnsTheScriptsReply() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.just(List.of(3L, 42L)));

        List<Long> reply = new LuaScriptExecutor(redis).execute(script, List.of("k"), List.of("1")).block();

        assertThat(reply).containsExactly(3L, 42L);
    }

    @Test
    void aRedisFailureSurfacesAsRedisUnavailable() {
        RedisConnectionFailureException cause = new RedisConnectionFailureException("down");
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn((Flux) Flux.error(cause));

        assertThatThrownBy(() -> new LuaScriptExecutor(redis).execute(script, List.of("k"), List.of("1")).block())
                .isInstanceOf(RedisUnavailableException.class)
                .hasCause(cause);
    }
}
