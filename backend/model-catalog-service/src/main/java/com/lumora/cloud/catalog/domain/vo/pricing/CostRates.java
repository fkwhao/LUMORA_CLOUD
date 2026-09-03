package com.lumora.cloud.catalog.domain.vo.pricing;

import java.math.BigDecimal;

public record CostRates(
        BigDecimal uncachedInputPerMillion,
        BigDecimal cachedInputPerMillion,
        BigDecimal cacheCreationInputPerMillion,
        BigDecimal outputPerMillion
) {
}
