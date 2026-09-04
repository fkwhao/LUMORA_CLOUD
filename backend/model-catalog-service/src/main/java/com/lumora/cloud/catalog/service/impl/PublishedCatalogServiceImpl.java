package com.lumora.cloud.catalog.service.impl;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.CostRates;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.catalog.error.ApiException;
import com.lumora.cloud.catalog.domain.entity.model.ModelVersionEntity;
import com.lumora.cloud.catalog.mapper.query.CatalogQueryMapper;
import com.lumora.cloud.catalog.domain.vo.model.PublicModelResponse;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingRule;
import com.lumora.cloud.catalog.cache.PublishedCatalogCache;
import com.lumora.cloud.catalog.service.IPublishedCatalogService;
import com.lumora.cloud.catalog.support.ModelRouteService;
import com.lumora.cloud.catalog.support.TimePricingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublishedCatalogServiceImpl implements IPublishedCatalogService {

    private final CatalogQueryMapper queryMapper;
    private final PublishedCatalogCache cache;
    private final TimePricingPolicyService timePricingPolicyService;
    private final ModelRouteService routeService;

    @Transactional(readOnly = true)
    @Override
    public List<ResolvedModelConfig> resolvedModels() {
        return cache.getOrLoad(this::loadFromMySql);
    }

    @Override
    public ResolvedModelConfig resolve(String modelCode) {
        String normalized = modelCode.trim().toLowerCase(java.util.Locale.ROOT);
        return resolvedModels().stream()
                .filter(model -> model.modelCode().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE", "模型当前不可用"));
    }

    @Override
    public List<PublicModelResponse> publicModels() {
        return resolvedModels().stream().map(this::publicResponse).toList();
    }

    @Override
    public List<PublishedModelReference> publishedModelReferences() {
        return resolvedModels().stream()
                .map(model -> new PublishedModelReference(model.modelCode(), model.displayName()))
                .toList();
    }

    private List<ResolvedModelConfig> loadFromMySql() {
        return queryMapper.findPublishedModels().stream().map(this::resolved).toList();
    }

    private ResolvedModelConfig resolved(ModelVersionEntity entity) {
        List<com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute> routes =
                routeService.resolvedRoutes(entity.getId());
        ResolvedModelConfig model = new ResolvedModelConfig(
                entity.getModelCode(), entity.getDisplayName(), entity.getDescription(), entity.getVersionKey(),
                entity.getProviderCode(), entity.getProtocolType(), entity.getBaseUrl(),
                entity.getActiveCredentialReference(), entity.getUpstreamModel(), new ModelCapabilities(
                        entity.getContextWindow(), entity.getMaxOutputTokens(), entity.getSupportsReasoning(),
                        entity.getSupportsTools(), entity.getSupportsVision(), entity.getSupportsJson(),
                        entity.getSupportsWebSearch()
                ), entity.getCostCurrency(), new CostRates(
                        entity.getInputCostPerMillion(), entity.getCacheReadCostPerMillion(),
                        entity.getCacheWriteCostPerMillion(), entity.getOutputCostPerMillion()
                ), timePricingPolicyService.resolvedCostPolicy(entity), new QuotaRates(
                        entity.getInputQuotaPerMillion(), entity.getCacheReadQuotaPerMillion(),
                        entity.getCacheWriteQuotaPerMillion(), entity.getOutputQuotaPerMillion(),
                        entity.getMinimumRequestQuota()
                ), timePricingPolicyService.resolvedQuotaPolicy(entity), entity.getPublishedAt(), routes
        );
        return routes.isEmpty() ? model : model.withRoute(routes.getFirst());
    }

    private PublicModelResponse publicResponse(ResolvedModelConfig model) {
        return new PublicModelResponse(
                model.modelCode(), model.displayName(), model.description(), model.pricingVersion(),
                model.providerCode(), model.capabilities(), model.quotaRates(), publicQuotaPolicy(model),
                model.publishedAt()
        );
    }

    private QuotaTimePricingPolicy publicQuotaPolicy(ResolvedModelConfig model) {
        if (model.quotaTimePricingPolicy() == null) {
            return null;
        }
        return new QuotaTimePricingPolicy(
                model.quotaTimePricingPolicy().zoneId(),
                model.quotaTimePricingPolicy().defaultQuotaMultiplier(),
                model.quotaTimePricingPolicy().rules().stream()
                        .map(rule -> new QuotaTimePricingRule(
                                rule.name(), rule.daysOfWeek(), rule.startTime(), rule.endTime(),
                                rule.quotaMultiplier()
                        ))
                        .toList()
        );
    }
}
