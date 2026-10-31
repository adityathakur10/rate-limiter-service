package com.ratelimiter.core.controller;

import com.ratelimiter.common.dto.RateLimitRequestDto;
import com.ratelimiter.common.dto.RateLimitResponseDto;
import com.ratelimiter.core.algorithm.Decision;
import com.ratelimiter.core.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class RateLimitController {

    private final RateLimitService rateLimitService;

    public RateLimitController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/check")
    public Mono<ResponseEntity<RateLimitResponseDto>> check(@Valid @RequestBody RateLimitRequestDto request) {
        return rateLimitService.check(request).map(RateLimitController::toResponse);
    }

    // X-RateLimit-Reset is the window end in epoch seconds; Retry-After is seconds to wait.
    private static ResponseEntity<RateLimitResponseDto> toResponse(Decision decision) {
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(decision.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(decision.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(decision.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(decision.resetAt().getEpochSecond()));
        if (decision.allowed()) {
            return response.body(new RateLimitResponseDto(true, decision.remaining(), decision.resetAt(), null));
        }
        return response
                .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                .body(new RateLimitResponseDto(false, 0, null, decision.retryAfterSeconds()));
    }
}
