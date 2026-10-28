package com.ratelimiter.common.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields) {
}
