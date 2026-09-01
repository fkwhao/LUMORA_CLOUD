package com.lumora.cloud.modelgateway.service;

import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaTimePricingPolicy;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaTimePricingRule;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class QuotaCalculator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final int SCALE = 6;

    public PricingSnapshot snapshot(ResolvedModelConfig model, Instant pricingAt) {
        QuotaTimePricingPolicy policy = model.quotaTimePricingPolicy();
        if (policy == null) {
            return new PricingSnapshot(pricingAt, BigDecimal.ONE, null);
        }
        var local = pricingAt.atZone(ZoneId.of(policy.zoneId()));
        QuotaTimePricingRule matched = policy.rules().stream()
                .filter(rule -> matches(rule, local.getDayOfWeek(), local.toLocalTime()))
                .findFirst()
                .orElse(null);
        return matched == null
                ? new PricingSnapshot(pricingAt, positive(policy.defaultQuotaMultiplier()), null)
                : new PricingSnapshot(pricingAt, positive(matched.quotaMultiplier()), matched.name());
    }

    public BigDecimal maximum(
            ResolvedModelConfig model,
            long requestedMaxOutputTokens,
            PricingSnapshot snapshot
    ) {
        QuotaRates rates = model.quotaRates();
        BigDecimal inputRate = max(
                rates.uncachedInputPerMillion(),
                rates.cachedInputPerMillion(),
                rates.cacheCreationInputPerMillion()
        );
        long outputLimit = Math.min(model.capabilities().maxOutputTokens(), requestedMaxOutputTokens);
        BigDecimal estimated = charge(model.capabilities().contextWindow(), inputRate)
                .add(charge(outputLimit, rates.outputPerMillion()));
        return billable(estimated, rates.minimumRequestQuota(), snapshot.quotaMultiplier());
    }

    public BigDecimal actual(ResolvedModelConfig model, TokenUsage usage, PricingSnapshot snapshot) {
        QuotaRates rates = model.quotaRates();
        BigDecimal total = charge(usage.inputTokens(), rates.uncachedInputPerMillion())
                .add(charge(usage.outputTokens(), rates.outputPerMillion()))
                .add(charge(usage.reasoningTokens(), rates.outputPerMillion()))
                .add(charge(usage.cacheReadTokens(), rates.cachedInputPerMillion()))
                .add(charge(usage.cacheWriteTokens(), rates.cacheCreationInputPerMillion()));
        return billable(total, rates.minimumRequestQuota(), snapshot.quotaMultiplier());
    }

    private BigDecimal charge(long tokens, BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(tokens)).divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal billable(BigDecimal calculated, BigDecimal minimum, BigDecimal multiplier) {
        BigDecimal result = calculated.max(minimum).multiply(positive(multiplier));
        if (result.signum() <= 0) {
            throw new IllegalStateException("Published model quota rates must produce a positive charge");
        }
        return result.setScale(SCALE, RoundingMode.CEILING);
    }

    private BigDecimal max(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first.max(second).max(third);
    }

    private boolean matches(QuotaTimePricingRule rule, DayOfWeek currentDay, LocalTime currentTime) {
        if (rule.startTime().isBefore(rule.endTime())) {
            return rule.daysOfWeek().contains(currentDay)
                    && !currentTime.isBefore(rule.startTime())
                    && currentTime.isBefore(rule.endTime());
        }
        if (rule.daysOfWeek().contains(currentDay) && !currentTime.isBefore(rule.startTime())) {
            return true;
        }
        DayOfWeek previousDay = currentDay == DayOfWeek.MONDAY
                ? DayOfWeek.SUNDAY
                : DayOfWeek.of(currentDay.getValue() - 1);
        return rule.daysOfWeek().contains(previousDay) && currentTime.isBefore(rule.endTime());
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException("Published model quota multiplier must be positive");
        }
        return value;
    }

    public record PricingSnapshot(
            Instant pricingAt,
            BigDecimal quotaMultiplier,
            String ruleName
    ) {
    }

}
