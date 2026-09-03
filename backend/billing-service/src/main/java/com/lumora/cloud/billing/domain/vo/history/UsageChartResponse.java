package com.lumora.cloud.billing.domain.vo.history;

import java.time.LocalDate;
import java.util.List;

public record UsageChartResponse(
        String range,
        LocalDate startsOn,
        LocalDate endsOnExclusive,
        String reportingZone,
        UsageHistorySummaryResponse summary,
        List<DailyUsageResponse> points
) {
    public UsageChartResponse {
        points = List.copyOf(points);
    }
}
