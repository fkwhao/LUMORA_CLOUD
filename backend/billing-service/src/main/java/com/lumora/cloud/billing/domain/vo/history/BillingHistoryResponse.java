package com.lumora.cloud.billing.domain.vo.history;

import java.time.Instant;
import java.util.List;

public record BillingHistoryResponse(
        String scope,
        Instant startsAt,
        Instant endsAt,
        String reportingZone,
        int detailLimit,
        QuotaHistorySummaryResponse quotaSummary,
        UsageHistorySummaryResponse usageSummary,
        List<LedgerEntryResponse> ledger,
        List<UsageResponse> usage
) {
    public BillingHistoryResponse {
        ledger = List.copyOf(ledger);
        usage = List.copyOf(usage);
    }
}
