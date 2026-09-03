package com.lumora.cloud.catalog.domain.dto.route;

import com.lumora.cloud.catalog.domain.dto.pricing.CostTimePricingPolicyInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModelRouteInput(
        @NotBlank @Size(max = 120) String routeName,
        @NotNull @Min(1) Long providerId,
        @NotBlank @Size(max = 160) String upstreamModel,
        @Min(0) @Max(10_000) int priority,
        @Min(1) @Max(10_000) int weight,
        @Min(1) @Max(1_000_000) Integer maxConcurrency,
        @Min(1) @Max(10_000_000) Integer requestsPerMinute,
        @Min(1) Long tokensPerMinute,
        boolean failoverEnabled,
        boolean circuitBreakerEnabled,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String costCurrency,
        @NotNull @DecimalMin("0") BigDecimal uncachedInputCostPerMillion,
        @NotNull @DecimalMin("0") BigDecimal cachedInputCostPerMillion,
        @DecimalMin("0") BigDecimal cacheCreationInputCostPerMillion,
        @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion,
        @Valid CostTimePricingPolicyInput costTimePricingPolicy
) {
}
