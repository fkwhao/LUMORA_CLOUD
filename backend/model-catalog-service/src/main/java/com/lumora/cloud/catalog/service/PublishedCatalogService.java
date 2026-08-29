package com.lumora.cloud.catalog.service;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.persistence.entity.ModelVersionEntity;
import com.lumora.cloud.catalog.persistence.mapper.CatalogQueryMapper;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublicModelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublishedCatalogService {

    private final CatalogQueryMapper queryMapper;
    private final PublishedCatalogCache cache;

    public PublishedCatalogService(CatalogQueryMapper queryMapper, PublishedCatalogCache cache) {
        this.queryMapper = queryMapper;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<ResolvedModelConfig> resolvedModels() {
        return cache.getOrLoad(this::loadFromMySql);
    }

    public ResolvedModelConfig resolve(String modelCode) {
        String normalized = modelCode.trim().toLowerCase(java.util.Locale.ROOT);
        return resolvedModels().stream()
                .filter(model -> model.modelCode().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE", "模型当前不可用"));
    }

    public List<PublicModelResponse> publicModels() {
        return resolvedModels().stream().map(this::publicResponse).toList();
    }

    private List<ResolvedModelConfig> loadFromMySql() {
        return queryMapper.findPublishedModels().stream().map(this::resolved).toList();
    }

    private ResolvedModelConfig resolved(ModelVersionEntity entity) {
        return new ResolvedModelConfig(
                entity.getModelCode(), entity.getDisplayName(), entity.getDescription(), entity.getVersionKey(),
                entity.getProviderCode(), entity.getProtocolType(), entity.getBaseUrl(),
                entity.getActiveCredentialReference(), entity.getUpstreamModel(), new ModelCapabilities(
                        entity.getContextWindow(), entity.getMaxOutputTokens(), entity.getSupportsReasoning(),
                        entity.getSupportsTools(), entity.getSupportsVision(), entity.getSupportsJson()
                ), new QuotaRates(
                        entity.getInputQuotaPerMillion(), entity.getOutputQuotaPerMillion(),
                        entity.getReasoningQuotaPerMillion(), entity.getCacheReadQuotaPerMillion(),
                        entity.getCacheWriteQuotaPerMillion(), entity.getMinimumRequestQuota()
                ), entity.getPublishedAt()
        );
    }

    private PublicModelResponse publicResponse(ResolvedModelConfig model) {
        return new PublicModelResponse(
                model.modelCode(), model.displayName(), model.description(), model.pricingVersion(),
                model.providerCode(), model.capabilities(), model.quotaRates(), model.publishedAt()
        );
    }
}
