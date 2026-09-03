package com.lumora.cloud.catalog.domain.vo.pricing;

import java.util.List;

public record CostTimePricingPolicy(String zoneId, List<CostTimePricingRule> rules) {
    public CostTimePricingPolicy {
        rules = List.copyOf(rules);
    }
}
