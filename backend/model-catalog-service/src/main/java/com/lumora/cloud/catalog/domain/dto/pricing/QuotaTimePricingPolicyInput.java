package com.lumora.cloud.catalog.domain.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuotaTimePricingPolicyInput(
        @NotBlank @Size(max = 64) String zoneId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal defaultQuotaMultiplier,
        @NotNull @Size(min = 1, max = 32) List<@Valid QuotaTimePricingRuleInput> rules
) {
    public QuotaTimePricingPolicyInput {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
