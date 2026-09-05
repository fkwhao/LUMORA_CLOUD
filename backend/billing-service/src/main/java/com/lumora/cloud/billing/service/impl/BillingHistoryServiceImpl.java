package com.lumora.cloud.billing.service.impl;

import com.lumora.cloud.billing.domain.entity.quota.QuotaBucketEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import com.lumora.cloud.billing.domain.enums.BillingHistoryScope;
import com.lumora.cloud.billing.domain.enums.UsageChartRange;
import com.lumora.cloud.billing.domain.projection.history.DailyUsageAggregate;
import com.lumora.cloud.billing.domain.projection.history.QuotaLedgerAggregate;
import com.lumora.cloud.billing.domain.projection.history.UsageAggregate;
import com.lumora.cloud.billing.domain.vo.history.BillingHistoryResponse;
import com.lumora.cloud.billing.domain.vo.history.DailyUsageResponse;
import com.lumora.cloud.billing.domain.vo.history.LedgerEntryResponse;
import com.lumora.cloud.billing.domain.vo.history.QuotaHistorySummaryResponse;
import com.lumora.cloud.billing.domain.vo.history.UsageChartResponse;
import com.lumora.cloud.billing.domain.vo.history.UsageHistorySummaryResponse;
import com.lumora.cloud.billing.domain.vo.history.UsageResponse;
import com.lumora.cloud.billing.mapper.quota.QuotaDailySummaryMapper;
import com.lumora.cloud.billing.mapper.quota.QuotaLedgerMapper;
import com.lumora.cloud.billing.mapper.subscription.SubscriptionMapper;
import com.lumora.cloud.billing.mapper.usage.UsageDailySummaryMapper;
import com.lumora.cloud.billing.mapper.usage.UsageRecordMapper;
import com.lumora.cloud.billing.service.IBillingHistoryService;
import com.lumora.cloud.billing.support.QuotaBucketService;
import com.lumora.cloud.billing.utils.BillingAmounts;
import com.lumora.cloud.billing.utils.BillingReportingPeriods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingHistoryServiceImpl implements IBillingHistoryService {

    private static final int DETAIL_LIMIT = 100;

    private final QuotaLedgerMapper ledgerMapper;
    private final QuotaDailySummaryMapper quotaDailySummaryMapper;
    private final UsageRecordMapper usageMapper;
    private final UsageDailySummaryMapper usageDailySummaryMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final QuotaBucketService bucketService;

    @Override
    @Transactional
    public BillingHistoryResponse recent(Long userId, BillingHistoryScope scope) {
        return scope == BillingHistoryScope.CURRENT_MONTH
                ? currentMonth(userId, Instant.now())
                : currentPeriod(userId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public UsageChartResponse usageChart(Long userId, UsageChartRange range, LocalDate anchor) {
        LocalDate effectiveAnchor = anchor == null
                ? BillingReportingPeriods.localDate(Instant.now())
                : anchor;
        BillingReportingPeriods.DateRange period = range == UsageChartRange.MONTH
                ? BillingReportingPeriods.monthContaining(effectiveAnchor)
                : BillingReportingPeriods.weekContaining(effectiveAnchor);

        UsageAggregate aggregate = usageDailySummaryMapper.aggregate(
                userId, period.startsOn(), period.endsOnExclusive()
        );
        Map<LocalDate, DailyUsageAggregate> persisted = new HashMap<>();
        for (DailyUsageAggregate item : usageDailySummaryMapper.listDaily(
                userId, period.startsOn(), period.endsOnExclusive()
        )) {
            persisted.put(item.getSummaryDate(), item);
        }
        List<DailyUsageResponse> points = new ArrayList<>();
        for (LocalDate date = period.startsOn(); date.isBefore(period.endsOnExclusive()); date = date.plusDays(1)) {
            points.add(daily(date, persisted.get(date)));
        }
        return new UsageChartResponse(
                range.name(), period.startsOn(), period.endsOnExclusive(),
                BillingReportingPeriods.ZONE.getId(), usageSummary(aggregate), points
        );
    }

    private BillingHistoryResponse currentPeriod(Long userId, Instant now) {
        SubscriptionEntity subscription = subscriptionMapper.findActiveForUpdate(userId, now);
        if (subscription == null) {
            return empty(BillingHistoryScope.CURRENT_PERIOD);
        }
        QuotaBucketEntity bucket = bucketService.currentForUpdate(subscription, now);

        return response(
                BillingHistoryScope.CURRENT_PERIOD,
                bucket.getStartsAt(),
                bucket.getEndsAt(),
                quotaSummary(bucket, ledgerMapper.countByBucket(bucket.getId())),
                usageSummary(usageMapper.aggregateUserByBucket(userId, bucket.getId())),
                ledgerMapper.findRecentByBucket(bucket.getId(), DETAIL_LIMIT),
                usageMapper.findRecentByBucket(userId, bucket.getId(), DETAIL_LIMIT)
        );
    }

    private BillingHistoryResponse currentMonth(Long userId, Instant now) {
        BillingReportingPeriods.DateRange month = BillingReportingPeriods.currentMonth(now);
        return response(
                BillingHistoryScope.CURRENT_MONTH,
                month.startsAt(),
                month.endsAt(),
                quotaSummary(quotaDailySummaryMapper.aggregate(
                        userId, month.startsOn(), month.endsOnExclusive()
                )),
                usageSummary(usageDailySummaryMapper.aggregate(
                        userId, month.startsOn(), month.endsOnExclusive()
                )),
                ledgerMapper.findRecentByUserBetween(userId, month.startsAt(), month.endsAt(), DETAIL_LIMIT),
                usageMapper.findRecentByUserBetween(userId, month.startsAt(), month.endsAt(), DETAIL_LIMIT)
        );
    }

    private BillingHistoryResponse response(
            BillingHistoryScope scope,
            Instant startsAt,
            Instant endsAt,
            QuotaHistorySummaryResponse quotaSummary,
            UsageHistorySummaryResponse usageSummary,
            List<QuotaLedgerEntity> ledger,
            List<UsageRecordEntity> usage
    ) {
        return new BillingHistoryResponse(
                scope.name(), startsAt, endsAt, BillingReportingPeriods.ZONE.getId(), DETAIL_LIMIT,
                quotaSummary, usageSummary,
                ledger.stream().map(this::ledger).toList(),
                usage.stream().map(this::usage).toList()
        );
    }

    private BillingHistoryResponse empty(BillingHistoryScope scope) {
        return new BillingHistoryResponse(
                scope.name(), null, null, BillingReportingPeriods.ZONE.getId(), DETAIL_LIMIT,
                quotaSummary(null, 0L), emptyUsage(), List.of(), List.of()
        );
    }

    private QuotaHistorySummaryResponse quotaSummary(QuotaBucketEntity bucket, long entryCount) {
        if (bucket == null) {
            return new QuotaHistorySummaryResponse(
                    entryCount, BillingAmounts.zero(), BillingAmounts.zero(), BillingAmounts.zero()
            );
        }
        return new QuotaHistorySummaryResponse(
                entryCount, bucket.getGrantedQuota(), bucket.getReservedQuota(), bucket.getConsumedQuota()
        );
    }

    private QuotaHistorySummaryResponse quotaSummary(QuotaLedgerAggregate aggregate) {
        if (aggregate == null) {
            return quotaSummary(null, 0L);
        }
        return new QuotaHistorySummaryResponse(
                number(aggregate.getEntryCount()), amount(aggregate.getGrantedDelta()),
                amount(aggregate.getReservedDelta()), amount(aggregate.getConsumedDelta())
        );
    }

    private UsageHistorySummaryResponse usageSummary(UsageAggregate aggregate) {
        if (aggregate == null) {
            return emptyUsage();
        }
        return new UsageHistorySummaryResponse(
                number(aggregate.getRequestCount()), number(aggregate.getCompletedCount()),
                number(aggregate.getPendingCount()), number(aggregate.getFailedCount()),
                number(aggregate.getInputTokens()), number(aggregate.getOutputTokens()),
                number(aggregate.getReasoningTokens()), number(aggregate.getCacheReadTokens()),
                number(aggregate.getCacheWriteTokens()), amount(aggregate.getBilledQuota())
        );
    }

    private UsageHistorySummaryResponse emptyUsage() {
        return new UsageHistorySummaryResponse(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, BillingAmounts.zero()
        );
    }

    private DailyUsageResponse daily(LocalDate date, DailyUsageAggregate aggregate) {
        if (aggregate == null) {
            return new DailyUsageResponse(date, 0L, 0L, 0L, 0L, 0L, 0L, BillingAmounts.zero());
        }
        return new DailyUsageResponse(
                date, number(aggregate.getRequestCount()), number(aggregate.getInputTokens()),
                number(aggregate.getOutputTokens()), number(aggregate.getReasoningTokens()),
                number(aggregate.getCacheReadTokens()), number(aggregate.getCacheWriteTokens()),
                amount(aggregate.getBilledQuota())
        );
    }

    private LedgerEntryResponse ledger(QuotaLedgerEntity entry) {
        return new LedgerEntryResponse(
                entry.getId(), entry.getEntryType(), entry.getReferenceType(), entry.getReferenceId(),
                entry.getGrantedDelta(), entry.getReservedDelta(), entry.getConsumedDelta(),
                entry.getDescription(), entry.getCreatedAt()
        );
    }

    private UsageResponse usage(UsageRecordEntity record) {
        return new UsageResponse(
                record.getUsageId(), record.getRequestId(), record.getModelCode(), record.getPricingVersion(),
                record.getInputTokens(), record.getOutputTokens(), record.getReasoningTokens(),
                record.getCacheReadTokens(), record.getCacheWriteTokens(), record.getBilledQuota(),
                record.getStatus(), record.getOccurredAt()
        );
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BillingAmounts.zero() : value;
    }
}
