package com.lumora.cloud.billing.domain.vo.quota;

import java.math.BigDecimal;
import java.time.Instant;

public record QuotaResponse(
        String bucketId,
        int periodNo,
        Instant startsAt,
        Instant endsAt,
        BigDecimal granted,
        BigDecimal reserved,
        BigDecimal consumed,
        BigDecimal remaining
) {
}
