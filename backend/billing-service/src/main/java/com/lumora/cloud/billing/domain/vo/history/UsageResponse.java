package com.lumora.cloud.billing.domain.vo.history;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageResponse(
        String usageId,
        String requestId,
        String modelCode,
        String pricingVersion,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        BigDecimal billedQuota,
        String status,
        Instant occurredAt
) {
}
