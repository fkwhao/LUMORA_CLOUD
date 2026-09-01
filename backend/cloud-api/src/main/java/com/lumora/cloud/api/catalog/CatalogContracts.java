package com.lumora.cloud.api.catalog;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

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
            BigDecimal uncachedInputPerMillion,
            BigDecimal cachedInputPerMillion,
            BigDecimal cacheCreationInputPerMillion,
            BigDecimal outputPerMillion,
            BigDecimal minimumRequestQuota
    ) {
    }

    public record CostRates(
            BigDecimal uncachedInputPerMillion,
            BigDecimal cachedInputPerMillion,
            BigDecimal cacheCreationInputPerMillion,
            BigDecimal outputPerMillion
    ) {
    }

    public record CostTimePricingRule(
            String name,
            List<DayOfWeek> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            CostRates costRates
    ) {
        public CostTimePricingRule {
            daysOfWeek = List.copyOf(daysOfWeek);
        }
    }

    public record CostTimePricingPolicy(
            String zoneId,
            List<CostTimePricingRule> rules
    ) {
        public CostTimePricingPolicy {
            rules = List.copyOf(rules);
        }
    }

    public record QuotaTimePricingRule(
            String name,
            List<DayOfWeek> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal quotaMultiplier
    ) {
        public QuotaTimePricingRule {
            daysOfWeek = List.copyOf(daysOfWeek);
        }
    }

    public record QuotaTimePricingPolicy(
            String zoneId,
            BigDecimal defaultQuotaMultiplier,
            List<QuotaTimePricingRule> rules
    ) {
        public QuotaTimePricingPolicy {
            rules = List.copyOf(rules);
        }
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
            String costCurrency,
            CostRates costRates,
            CostTimePricingPolicy costTimePricingPolicy,
            QuotaRates quotaRates,
            QuotaTimePricingPolicy quotaTimePricingPolicy,
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
