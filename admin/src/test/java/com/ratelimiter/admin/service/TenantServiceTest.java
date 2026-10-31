package com.ratelimiter.admin.service;

import com.ratelimiter.admin.entity.Tenant;
import com.ratelimiter.admin.exception.ResourceNotFoundException;
import com.ratelimiter.admin.mapper.TenantMapper;
import com.ratelimiter.admin.repository.TenantRepository;
import com.ratelimiter.admin.utils.ApiKeyGenerator;
import com.ratelimiter.common.dto.TenantDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, apiKeyGenerator, new TenantMapper());
    }

    @Test
    void createSavesTenantWithGeneratedKeyAndReturnsItInTheDto() {
        when(apiKeyGenerator.generate()).thenReturn("rl_generated");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantDto result = tenantService.create(new TenantDto(null, "acme", null, null));

        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("acme");
        assertThat(saved.getValue().getApiKey()).isEqualTo("rl_generated");
        assertThat(result.name()).isEqualTo("acme");
        assertThat(result.apiKey()).isEqualTo("rl_generated");
    }

    @Test
    void createIgnoresAnApiKeyTheCallerSends() {
        when(apiKeyGenerator.generate()).thenReturn("rl_generated");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantDto result = tenantService.create(new TenantDto(null, "acme", "rl_chosen_by_caller", null));

        assertThat(result.apiKey()).isEqualTo("rl_generated");
    }

    @Test
    void getByIdReturnsTheTenant() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setName("acme");
        tenant.setApiKey("rl_abc");
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        TenantDto result = tenantService.getById(id);

        assertThat(result.name()).isEqualTo("acme");
        assertThat(result.apiKey()).isEqualTo("rl_abc");
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
