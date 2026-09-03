package com.lumora.cloud.billing.domain.vo.history;

import java.math.BigDecimal;

public record UsageHistorySummaryResponse(
        long requestCount,
        long completedCount,
        long pendingCount,
        long failedCount,
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
