package com.ratelimiter.admin.controller;

import com.ratelimiter.admin.service.TenantService;
import com.ratelimiter.common.dto.TenantDto;
import com.ratelimiter.common.reactive.BlockingCall;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public Mono<ResponseEntity<TenantDto>> create(@Valid @RequestBody TenantDto request) {
        return BlockingCall.call(() -> tenantService.create(request))
                .map(tenant -> ResponseEntity.status(HttpStatus.CREATED).body(tenant));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<TenantDto>> getById(@PathVariable UUID id) {
        return BlockingCall.call(() -> tenantService.getById(id))
                .map(tenant -> ResponseEntity.status(HttpStatus.OK).body(tenant));
    }
}
