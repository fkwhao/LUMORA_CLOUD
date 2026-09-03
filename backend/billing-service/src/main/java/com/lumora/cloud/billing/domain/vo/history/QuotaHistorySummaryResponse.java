package com.lumora.cloud.billing.domain.vo.history;

import java.math.BigDecimal;

public record QuotaHistorySummaryResponse(
        long entryCount,
        BigDecimal grantedDelta,
        BigDecimal reservedDelta,
        BigDecimal consumedDelta
) {
}
