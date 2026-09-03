package com.lumora.cloud.billing.domain.vo.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminBillingStatisticsResponse(
        long publishedPlans,
        long activeSubscriptions,
        long pendingOrders,
        long fulfilledOrdersThisMonth,
        List<CurrencyRevenueResponse> revenueThisMonth,
        long modelRequestsToday,
        long completedModelRequestsToday,
        long pendingReconciliationToday,
        BigDecimal billedQuotaToday,
        String reportingZone,
        Instant generatedAt
) {
    public AdminBillingStatisticsResponse {
        revenueThisMonth = List.copyOf(revenueThisMonth);
    }
}
