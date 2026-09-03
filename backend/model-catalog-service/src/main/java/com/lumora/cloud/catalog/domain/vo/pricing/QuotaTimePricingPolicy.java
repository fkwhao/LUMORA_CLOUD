package com.lumora.cloud.catalog.domain.vo.pricing;

import java.math.BigDecimal;
import java.util.List;

public record QuotaTimePricingPolicy(
        String zoneId,
        BigDecimal defaultQuotaMultiplier,
        List<QuotaTimePricingRule> rules
) {
    public QuotaTimePricingPolicy {
        rules = List.copyOf(rules);
    }
}
