package com.lumora.cloud.catalog.domain.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModelVersionInput(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 160) String upstreamModel,
        @Min(1) long contextWindow,
        @Min(1) long maxOutputTokens,
        boolean supportsReasoning,
        boolean supportsTools,
        boolean supportsVision,
        boolean supportsJson,
        boolean supportsWebSearch,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String costCurrency,
        @NotNull @DecimalMin("0") BigDecimal uncachedInputCostPerMillion,
        @NotNull @DecimalMin("0") BigDecimal cachedInputCostPerMillion,
        @DecimalMin("0") BigDecimal cacheCreationInputCostPerMillion,
        @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion,
        @Valid CostTimePricingPolicyInput costTimePricingPolicy,
        @NotNull @DecimalMin("0") BigDecimal uncachedInputQuotaPerMillion,
        @NotNull @DecimalMin("0") BigDecimal cachedInputQuotaPerMillion,
        @DecimalMin("0") BigDecimal cacheCreationInputQuotaPerMillion,
        @NotNull @DecimalMin("0") BigDecimal outputQuotaPerMillion,
        @NotNull @DecimalMin("0") BigDecimal minimumRequestQuota,
        @Valid QuotaTimePricingPolicyInput quotaTimePricingPolicy
) {
}
