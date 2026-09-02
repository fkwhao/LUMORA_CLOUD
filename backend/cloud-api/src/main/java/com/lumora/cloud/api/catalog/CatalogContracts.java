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
            boolean json,
            boolean webSearch
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
            Instant publishedAt,
            List<ResolvedModelRoute> routes
    ) {
        public ResolvedModelConfig {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }

        public ResolvedModelConfig(
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
            this(modelCode, displayName, description, pricingVersion, providerCode, protocolType,
                    baseUrl, credentialReference, upstreamModel, capabilities, costCurrency, costRates,
                    costTimePricingPolicy, quotaRates, quotaTimePricingPolicy, publishedAt, List.of());
        }

        public ResolvedModelConfig withRoute(ResolvedModelRoute route) {
            return new ResolvedModelConfig(
                    modelCode, displayName, description, pricingVersion,
                    route.providerCode(), route.protocolType(), route.baseUrl(),
                    route.credentialReference(), route.upstreamModel(), capabilities,
                    route.costCurrency(), route.costRates(), route.costTimePricingPolicy(),
                    quotaRates, quotaTimePricingPolicy, publishedAt, routes
            );
        }
    }

    public record ResolvedModelRoute(
            String routeId,
            String routeName,
            Long providerId,
            String providerCode,
            String protocolType,
            String baseUrl,
            String credentialReference,
            String upstreamModel,
            int priority,
            int weight,
            Integer maxConcurrency,
            Integer requestsPerMinute,
            Long tokensPerMinute,
            Integer accountMaxConcurrency,
            Integer accountRequestsPerMinute,
            Long accountTokensPerMinute,
            boolean failoverEnabled,
            boolean circuitBreakerEnabled,
            String costCurrency,
            CostRates costRates,
            CostTimePricingPolicy costTimePricingPolicy
    ) {
    }

    public record ResolvedProviderCredential(
            String credentialReference,
            String secret,
            String fingerprint,
            Instant rotatedAt
    ) {
    }

    public record PublishedModelReference(String modelCode, String displayName) {
    }
}
