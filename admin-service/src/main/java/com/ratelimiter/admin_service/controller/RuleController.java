package com.ratelimiter.admin_service.controller;

import com.ratelimiter.admin_service.service.RuleService;
import com.ratelimiter.common.dto.RuleDto;
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
    public ResponseEntity<RuleDto> create(@Valid @RequestBody RuleDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<RuleDto>> listByTenant(@RequestParam UUID tenantId) {
        return ResponseEntity.status(HttpStatus.OK).body(ruleService.listByTenant(tenantId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleDto> update(@PathVariable UUID id, @Valid @RequestBody RuleDto request) {
        return ResponseEntity.status(HttpStatus.OK).body(ruleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ruleService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }
}
