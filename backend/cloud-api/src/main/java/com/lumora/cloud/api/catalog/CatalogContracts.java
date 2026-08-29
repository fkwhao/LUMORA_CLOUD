package com.lumora.cloud.api.catalog;

import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogContracts {

    private CatalogContracts() {
    }

    public record ModelCapabilities(
            long contextWindow,
            long maxOutputTokens,
            boolean reasoning,
            boolean tools,
            boolean vision,
            boolean json
    ) {
    }

    public record QuotaRates(
            BigDecimal inputPerMillion,
            BigDecimal outputPerMillion,
            BigDecimal reasoningPerMillion,
            BigDecimal cacheReadPerMillion,
            BigDecimal cacheWritePerMillion,
            BigDecimal minimumRequestQuota
    ) {
    }

    public record ResolvedModelConfig(
            String modelCode,
            String displayName,
            String description,
            String pricingVersion,
            String providerCode,
            String protocolType,
            String baseUrl,
            String credentialReference,
            String upstreamModel,
            ModelCapabilities capabilities,
            QuotaRates quotaRates,
            Instant publishedAt
    ) {
    }

    public record ResolvedProviderCredential(
            String credentialReference,
            String secret,
            String fingerprint,
            Instant rotatedAt
    ) {
    }
}
