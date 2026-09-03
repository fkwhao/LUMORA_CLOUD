package com.lumora.cloud.billing.domain.vo.history;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyUsageResponse(
        LocalDate date,
        long requestCount,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        BigDecimal billedQuota
) {
    public long totalTokens() {
        return Math.addExact(
                Math.addExact(inputTokens, outputTokens),
                Math.addExact(reasoningTokens, Math.addExact(cacheReadTokens, cacheWriteTokens))
        );
    }
}
