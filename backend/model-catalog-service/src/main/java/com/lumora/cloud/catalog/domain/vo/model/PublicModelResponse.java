package com.lumora.cloud.catalog.domain.vo.model;

import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.catalog.domain.vo.pricing.QuotaTimePricingPolicy;

import java.time.Instant;

public record PublicModelResponse(
        String code,
        String displayName,
        String description,
        String pricingVersion,
        String providerCode,
        ModelCapabilities capabilities,
        QuotaRates quotaRates,
        QuotaTimePricingPolicy quotaTimePricingPolicy,
        Instant publishedAt
) {
}
