package com.lumora.cloud.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.catalog.domain.CatalogTypes.ProviderStatus;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.persistence.entity.ProviderEntity;
import com.lumora.cloud.catalog.persistence.mapper.ProviderMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateProviderRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ProviderResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateProviderRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ProviderService {

    private final ProviderMapper providerMapper;
    private final CatalogInputMapper inputMapper;
    private final PublishedCatalogCache cache;
    private final ProviderCredentialService credentialService;

    public ProviderService(
            ProviderMapper providerMapper,
            CatalogInputMapper inputMapper,
            PublishedCatalogCache cache,
            ProviderCredentialService credentialService
    ) {
        this.providerMapper = providerMapper;
        this.inputMapper = inputMapper;
        this.cache = cache;
        this.credentialService = credentialService;
    }

    @Transactional
    public ProviderResponse create(CreateProviderRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (providerMapper.findByCodeForUpdate(code) != null) {
            throw duplicateCode();
        }
        String credentialReference = credentialService.newReference();
        ProviderEntity entity = ProviderEntity.create(
                code, request.name().trim(), inputMapper.protocolType(request.protocolType()),
                inputMapper.baseUrl(request.baseUrl()), credentialReference,
                request.maxConcurrency(), request.requestsPerMinute(), request.tokensPerMinute()
        );
        try {
            providerMapper.insert(entity);
            credentialService.create(entity.getId(), credentialReference, request.apiKey());
        } catch (DuplicateKeyException exception) {
            throw duplicateCode();
        }
        return response(providerMapper.selectById(entity.getId()));
    }

    @Transactional
    public ProviderResponse update(Long providerId, UpdateProviderRequest request) {
        ProviderEntity existing = requireForUpdate(providerId);
        if (existing.getRevision() != request.expectedRevision()) {
            throw concurrentUpdate();
        }
        ProviderStatus status = ProviderStatus.valueOf(request.status());
        int updated = providerMapper.updateOptimistic(
                providerId, request.expectedRevision(), request.name().trim(),
                inputMapper.protocolType(request.protocolType()), inputMapper.baseUrl(request.baseUrl()),
                request.maxConcurrency(), request.requestsPerMinute(), request.tokensPerMinute(),
                status.name()
        );
        if (updated != 1) {
            throw concurrentUpdate();
        }
        cache.evictAfterCommit();
        return response(providerMapper.selectById(providerId));
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> list() {
        List<ProviderEntity> providers = providerMapper.selectList(Wrappers.<ProviderEntity>lambdaQuery()
                .orderByAsc(ProviderEntity::getId));
        var statuses = credentialService.statuses(providers.stream().map(ProviderEntity::getId).toList());
        return providers.stream().map(provider -> response(
                provider,
                statuses.getOrDefault(provider.getId(),
                        credentialService.environmentStatus(provider.getCredentialReference()))
        )).toList();
    }

    @Transactional
    public ProviderResponse rotateCredential(Long providerId, RotateProviderCredentialRequest request) {
        ProviderEntity provider = requireForUpdate(providerId);
        if (provider.getRevision() != request.expectedRevision()) {
            throw concurrentUpdate();
        }
        ProviderCredentialService.Rotation rotation = credentialService.rotate(providerId, request.apiKey());
        if (providerMapper.rotateCredentialReference(
                providerId, request.expectedRevision(), rotation.reference()
        ) != 1) {
            throw concurrentUpdate();
        }
        cache.evictAfterCommit();
        return response(providerMapper.selectById(providerId));
    }

    ProviderEntity requireActiveForUpdate(Long providerId) {
        ProviderEntity provider = requireForUpdate(providerId);
        if (!ProviderStatus.ACTIVE.name().equals(provider.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_DISABLED", "供应商当前已停用");
        }
        return provider;
    }

    ProviderEntity requireForUpdate(Long providerId) {
        ProviderEntity provider = providerMapper.findByIdForUpdate(providerId);
        if (provider == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "模型供应商不存在");
        }
        return provider;
    }

    private ProviderResponse response(ProviderEntity entity) {
        return response(entity, credentialService.status(entity.getId(), entity.getCredentialReference()));
    }

    private ProviderResponse response(
            ProviderEntity entity,
            com.lumora.cloud.catalog.web.CatalogWebContracts.ProviderCredentialStatusResponse credential
    ) {
        return new ProviderResponse(
                entity.getId(), entity.getCode(), entity.getName(), entity.getProtocolType(), entity.getBaseUrl(),
                entity.getMaxConcurrency(), entity.getRequestsPerMinute(), entity.getTokensPerMinute(),
                credential, entity.getStatus(), entity.getRevision(), entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiException duplicateCode() {
        return new ApiException(HttpStatus.CONFLICT, "PROVIDER_CODE_EXISTS", "供应商编码已经存在");
    }

    private ApiException concurrentUpdate() {
        return new ApiException(HttpStatus.CONFLICT, "PROVIDER_REVISION_CONFLICT",
                "供应商配置已被其他操作更新，请刷新后重试");
    }
}
