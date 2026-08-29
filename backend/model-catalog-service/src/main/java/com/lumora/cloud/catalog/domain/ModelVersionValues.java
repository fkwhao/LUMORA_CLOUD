package com.lumora.cloud.catalog.domain;

import java.math.BigDecimal;

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
        String costCurrency,
        BigDecimal inputCostPerMillion,
        BigDecimal outputCostPerMillion,
        BigDecimal reasoningCostPerMillion,
        BigDecimal cacheReadCostPerMillion,
        BigDecimal cacheWriteCostPerMillion,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal reasoningQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion,
        BigDecimal cacheWriteQuotaPerMillion,
        BigDecimal minimumRequestQuota
) {
}
