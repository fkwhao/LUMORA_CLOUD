package com.lumora.cloud.catalog.domain.vo.pricing;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

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
