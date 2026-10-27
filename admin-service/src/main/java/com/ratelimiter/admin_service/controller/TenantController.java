package com.ratelimiter.admin_service.controller;

import com.ratelimiter.admin_service.service.TenantService;
import com.ratelimiter.common.dto.TenantDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDto create(@Valid @RequestBody TenantDto request) {
        return tenantService.create(request);
    }

    @GetMapping("/{id}")
    public TenantDto getById(@PathVariable UUID id) {
        return tenantService.getById(id);
    }
}
