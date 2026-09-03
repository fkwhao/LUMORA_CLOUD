package com.lumora.cloud.catalog.domain.model;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record ModelVersionValues(
        String displayName,
        String description,
        String upstreamModel,
        long contextWindow,
        long maxOutputTokens,
        boolean supportsReasoning,
        boolean supportsTools,
        boolean supportsVision,
        boolean supportsJson,
        boolean supportsWebSearch,
        String costCurrency,
        BigDecimal inputCostPerMillion,
        BigDecimal outputCostPerMillion,
        BigDecimal cacheReadCostPerMillion,
        BigDecimal cacheWriteCostPerMillion,
        CostTimePricingPolicyValues costTimePricingPolicy,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion,
        BigDecimal cacheWriteQuotaPerMillion,
        BigDecimal minimumRequestQuota,
        QuotaTimePricingPolicyValues quotaTimePricingPolicy
) {

    public record CostRatesValues(
            BigDecimal inputPerMillion,
            BigDecimal outputPerMillion,
            BigDecimal cacheReadPerMillion,
            BigDecimal cacheWritePerMillion
    ) {
    }

    public record CostTimePricingPolicyValues(
            String zoneId,
            List<CostTimePricingRuleValues> rules
    ) {
        public CostTimePricingPolicyValues {
            rules = List.copyOf(rules);
        }
    }

    public record CostTimePricingRuleValues(
            String name,
            int daysMask,
            LocalTime startTime,
            LocalTime endTime,
            CostRatesValues costRates
    ) {
    }

    public record QuotaTimePricingPolicyValues(
            String zoneId,
            BigDecimal defaultQuotaMultiplier,
            List<QuotaTimePricingRuleValues> rules
    ) {
        public QuotaTimePricingPolicyValues {
            rules = List.copyOf(rules);
        }
    }

    public record QuotaTimePricingRuleValues(
            String name,
            int daysMask,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal quotaMultiplier
    ) {
    }
}
