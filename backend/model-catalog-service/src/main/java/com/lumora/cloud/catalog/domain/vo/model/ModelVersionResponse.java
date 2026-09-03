package com.lumora.cloud.catalog.domain.vo.model;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.catalog.domain.vo.pricing.CostRates;
import com.lumora.cloud.catalog.domain.vo.pricing.CostTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingPolicy;
import com.lumora.cloud.catalog.domain.vo.route.ModelRouteResponse;

import java.time.Instant;
import java.util.List;

public record ModelVersionResponse(
        String id,
        String pricingVersion,
        int versionNo,
        String status,
        long revision,
        Long providerId,
        String displayName,
        String description,
        String upstreamModel,
        String protocolType,
        String baseUrl,
        String credentialReference,
        ModelCapabilities capabilities,
        String costCurrency,
        CostRates costRates,
        CostTimePricingPolicy costTimePricingPolicy,
        QuotaRates quotaRates,
        QuotaTimePricingPolicy quotaTimePricingPolicy,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ModelRouteResponse> routes
) {
    public ModelVersionResponse {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
