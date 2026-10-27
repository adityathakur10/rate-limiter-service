package com.ratelimiter.admin_service.controller;

import com.ratelimiter.admin_service.service.RuleService;
import com.ratelimiter.common.dto.RuleDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    @ResponseStatus(HttpStatus.CREATED)
    public RuleDto create(@Valid @RequestBody RuleDto request) {
        return ruleService.create(request);
    }

    @GetMapping
    public List<RuleDto> listByTenant(@RequestParam UUID tenantId) {
        return ruleService.listByTenant(tenantId);
    }

    @PutMapping("/{id}")
    public RuleDto update(@PathVariable UUID id, @Valid @RequestBody RuleDto request) {
        return ruleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ruleService.delete(id);
    }
}
