package com.lumora.cloud.billing.domain.vo.history;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        String id,
        String entryType,
        String referenceType,
        String referenceId,
        BigDecimal grantedDelta,
        BigDecimal reservedDelta,
        BigDecimal consumedDelta,
        String description,
        Instant createdAt
) {
}
