package com.lumora.cloud.catalog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.domain.enums.ProviderStatus;
import com.lumora.cloud.catalog.domain.entity.provider.ProviderEntity;
import com.lumora.cloud.catalog.domain.model.CredentialRotation;
import com.lumora.cloud.catalog.mapper.provider.ProviderMapper;
import com.lumora.cloud.catalog.domain.dto.provider.CreateProviderRequest;
import com.lumora.cloud.catalog.domain.vo.provider.ProviderResponse;
import com.lumora.cloud.catalog.domain.dto.provider.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.domain.dto.provider.UpdateProviderRequest;
import com.lumora.cloud.catalog.cache.PublishedCatalogCache;
import com.lumora.cloud.catalog.service.IProviderCredentialService;
import com.lumora.cloud.catalog.service.IProviderService;
import com.lumora.cloud.catalog.support.ProviderAccessService;
import com.lumora.cloud.catalog.utils.CatalogInputMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ProviderServiceImpl implements IProviderService {

    private final ProviderMapper providerMapper;
    private final CatalogInputMapper inputMapper;
    private final PublishedCatalogCache cache;
    private final IProviderCredentialService credentialService;
    private final ProviderAccessService providerAccessService;

    public ProviderServiceImpl(
            ProviderMapper providerMapper,
            CatalogInputMapper inputMapper,
            PublishedCatalogCache cache,
            IProviderCredentialService credentialService,
            ProviderAccessService providerAccessService
    ) {
        this.providerMapper = providerMapper;
        this.inputMapper = inputMapper;
        this.cache = cache;
        this.credentialService = credentialService;
        this.providerAccessService = providerAccessService;
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
        ProviderEntity existing = providerAccessService.requireForUpdate(providerId);
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
        ProviderEntity provider = providerAccessService.requireForUpdate(providerId);
        if (provider.getRevision() != request.expectedRevision()) {
            throw concurrentUpdate();
        }
        CredentialRotation rotation = credentialService.rotate(providerId, request.apiKey());
        if (providerMapper.rotateCredentialReference(
                providerId, request.expectedRevision(), rotation.reference()
        ) != 1) {
            throw concurrentUpdate();
        }
        cache.evictAfterCommit();
        return response(providerMapper.selectById(providerId));
    }

    private ProviderResponse response(ProviderEntity entity) {
        return response(entity, credentialService.status(entity.getId(), entity.getCredentialReference()));
    }

    private ProviderResponse response(
            ProviderEntity entity,
            com.lumora.cloud.catalog.domain.vo.provider.ProviderCredentialStatusResponse credential
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
