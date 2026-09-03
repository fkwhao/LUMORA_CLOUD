package com.lumora.cloud.catalog.domain.vo.pricing;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

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
