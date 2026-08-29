package com.lumora.cloud.modelgateway.service;

import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class QuotaCalculator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final int SCALE = 6;

    public BigDecimal maximum(ResolvedModelConfig model, long requestedMaxOutputTokens) {
        QuotaRates rates = model.quotaRates();
        BigDecimal inputRate = max(rates.inputPerMillion(), rates.cacheReadPerMillion(), rates.cacheWritePerMillion());
        BigDecimal outputRate = max(rates.outputPerMillion(), rates.reasoningPerMillion());
        long outputLimit = Math.min(model.capabilities().maxOutputTokens(), requestedMaxOutputTokens);
        BigDecimal estimated = charge(model.capabilities().contextWindow(), inputRate)
                .add(charge(outputLimit, outputRate));
        return billableMax(estimated, rates.minimumRequestQuota());
    }

    public BigDecimal actual(ResolvedModelConfig model, TokenUsage usage) {
        QuotaRates rates = model.quotaRates();
        BigDecimal total = charge(usage.inputTokens(), rates.inputPerMillion())
                .add(charge(usage.outputTokens(), rates.outputPerMillion()))
                .add(charge(usage.reasoningTokens(), rates.reasoningPerMillion()))
                .add(charge(usage.cacheReadTokens(), rates.cacheReadPerMillion()))
                .add(charge(usage.cacheWriteTokens(), rates.cacheWritePerMillion()));
        return billableMax(total, rates.minimumRequestQuota());
    }

    private BigDecimal charge(long tokens, BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(tokens)).divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal billableMax(BigDecimal calculated, BigDecimal minimum) {
        BigDecimal result = calculated.max(minimum);
        if (result.signum() <= 0) {
            throw new IllegalStateException("Published model quota rates must produce a positive charge");
        }
        return result.setScale(SCALE, RoundingMode.CEILING);
    }

    private BigDecimal max(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first.max(second).max(third);
    }

    private BigDecimal max(BigDecimal first, BigDecimal second) {
        return first.max(second);
    }
}
