package com.lumora.cloud.catalog.domain.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record QuotaTimePricingRuleInput(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Size(min = 1, max = 7) List<@NotNull DayOfWeek> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quotaMultiplier
) {
    public QuotaTimePricingRuleInput {
        daysOfWeek = daysOfWeek == null ? null : List.copyOf(daysOfWeek);
    }
}
