package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.enums.BillingHistoryScope;
import com.lumora.cloud.billing.domain.enums.UsageChartRange;
import com.lumora.cloud.billing.domain.vo.history.BillingHistoryResponse;
import com.lumora.cloud.billing.domain.vo.history.UsageChartResponse;

import java.time.LocalDate;

public interface IBillingHistoryService {

    BillingHistoryResponse recent(Long userId, BillingHistoryScope scope);

    UsageChartResponse usageChart(Long userId, UsageChartRange range, LocalDate anchor);
}
