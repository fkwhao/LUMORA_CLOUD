package com.lumora.cloud.billing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.persistence.entity.BillingPlanEntity;
import com.lumora.cloud.billing.persistence.mapper.BillingPlanMapper;
import com.lumora.cloud.billing.persistence.mapper.PurchaseOrderMapper;
import com.lumora.cloud.billing.persistence.mapper.SubscriptionMapper;
import com.lumora.cloud.billing.persistence.mapper.UsageRecordMapper;
import com.lumora.cloud.billing.web.BillingWebContracts.AdminBillingStatisticsResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.CurrencyRevenueResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class BillingStatisticsService {

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Shanghai");

    private final BillingPlanMapper planMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final PurchaseOrderMapper orderMapper;
    private final UsageRecordMapper usageMapper;

    public BillingStatisticsService(
            BillingPlanMapper planMapper,
            SubscriptionMapper subscriptionMapper,
            PurchaseOrderMapper orderMapper,
            UsageRecordMapper usageMapper
    ) {
        this.planMapper = planMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.orderMapper = orderMapper;
        this.usageMapper = usageMapper;
    }

    @Transactional(readOnly = true)
    public AdminBillingStatisticsResponse statistics() {
        Instant now = Instant.now();
        ZonedDateTime localNow = now.atZone(REPORTING_ZONE);
        Instant dayStart = localNow.toLocalDate().atStartOfDay(REPORTING_ZONE).toInstant();
        Instant nextDayStart = localNow.toLocalDate().plusDays(1).atStartOfDay(REPORTING_ZONE).toInstant();
        Instant monthStart = localNow.toLocalDate().withDayOfMonth(1)
                .atStartOfDay(REPORTING_ZONE).toInstant();
        Instant nextMonthStart = localNow.toLocalDate().withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(REPORTING_ZONE).toInstant();

        var revenue = orderMapper.revenueBetween(monthStart, nextMonthStart).stream()
                .map(item -> new CurrencyRevenueResponse(
                        item.getCurrency(), item.getAmountMinor(), item.getOrderCount()
                ))
                .toList();
        long fulfilledOrdersThisMonth = revenue.stream().mapToLong(CurrencyRevenueResponse::orderCount).sum();

        return new AdminBillingStatisticsResponse(
                planMapper.selectCount(Wrappers.<BillingPlanEntity>lambdaQuery()
                        .eq(BillingPlanEntity::getStatus, "ACTIVE")),
                subscriptionMapper.countActiveAt(now),
                orderMapper.countPayableAt(now),
                fulfilledOrdersThisMonth,
                revenue,
                usageMapper.countBetween(dayStart, nextDayStart),
                usageMapper.countByStatusBetween("COMPLETED", dayStart, nextDayStart),
                usageMapper.countByStatusBetween("PENDING_RECONCILIATION", dayStart, nextDayStart),
                usageMapper.sumBilledQuotaBetween(dayStart, nextDayStart),
                REPORTING_ZONE.getId(),
                now
        );
    }
}
