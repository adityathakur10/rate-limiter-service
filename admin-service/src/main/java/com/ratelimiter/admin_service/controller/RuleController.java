package com.ratelimiter.admin_service.controller;

import com.ratelimiter.admin_service.service.RuleService;
import com.ratelimiter.common.dto.RuleDto;
import com.ratelimiter.common.reactive.BlockingCall;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public Mono<ResponseEntity<RuleDto>> create(@Valid @RequestBody RuleDto request) {
        return BlockingCall.call(() -> ruleService.create(request))
                .map(rule -> ResponseEntity.status(HttpStatus.CREATED).body(rule));
    }

    @GetMapping
    public Mono<ResponseEntity<List<RuleDto>>> listByTenant(@RequestParam UUID tenantId) {
        return BlockingCall.call(() -> ruleService.listByTenant(tenantId))
                .map(rules -> ResponseEntity.status(HttpStatus.OK).body(rules));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RuleDto>> update(@PathVariable UUID id, @Valid @RequestBody RuleDto request) {
        return BlockingCall.call(() -> ruleService.update(id, request))
                .map(rule -> ResponseEntity.status(HttpStatus.OK).body(rule));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return BlockingCall.run(() -> ruleService.delete(id))
                .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build());
    }
}
