package com.lumora.cloud.catalog.domain.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CostTimePricingPolicyInput(
        @NotBlank @Size(max = 64) String zoneId,
        @NotNull @Size(min = 1, max = 32) List<@Valid CostTimePricingRuleInput> rules
) {
    public CostTimePricingPolicyInput {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
