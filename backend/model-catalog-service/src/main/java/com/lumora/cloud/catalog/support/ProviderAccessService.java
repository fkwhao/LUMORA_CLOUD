package com.lumora.cloud.catalog.support;

import com.lumora.cloud.catalog.domain.entity.provider.ProviderEntity;
import com.lumora.cloud.catalog.domain.enums.ProviderStatus;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.mapper.provider.ProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderAccessService {

    private final ProviderMapper providerMapper;

    public ProviderEntity requireActiveForUpdate(Long providerId) {
        ProviderEntity provider = requireForUpdate(providerId);
        if (!ProviderStatus.ACTIVE.name().equals(provider.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_DISABLED", "供应商当前已停用");
        }
        return provider;
    }

    public ProviderEntity requireForUpdate(Long providerId) {
        ProviderEntity provider = providerMapper.findByIdForUpdate(providerId);
        if (provider == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "模型供应商不存在");
        }
        return provider;
    }
}
