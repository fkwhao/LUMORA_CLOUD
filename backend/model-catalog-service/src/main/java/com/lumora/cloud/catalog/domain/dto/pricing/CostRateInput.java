package com.lumora.cloud.catalog.domain.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CostRateInput(
        @NotNull @DecimalMin("0") BigDecimal uncachedInputPerMillion,
        @NotNull @DecimalMin("0") BigDecimal cachedInputPerMillion,
        @DecimalMin("0") BigDecimal cacheCreationInputPerMillion,
        @NotNull @DecimalMin("0") BigDecimal outputPerMillion
) {
}
