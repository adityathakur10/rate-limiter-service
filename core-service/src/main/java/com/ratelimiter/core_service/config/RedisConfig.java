package com.ratelimiter.core_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Clock;

@Configuration
public class RedisConfig {

    // Keys and values are plain strings (counters, JSON caches), so no JDK serialization.
    @Bean
    public ReactiveRedisTemplate<String, String> rateLimiterRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }

    // Injected wherever "now" matters (window maths) so tests can pin the time.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
