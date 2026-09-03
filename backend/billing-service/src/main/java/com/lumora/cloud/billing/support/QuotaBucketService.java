package com.lumora.cloud.billing.support;

import com.lumora.cloud.billing.domain.enums.LedgerEntryType;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaBucketEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.quota.QuotaBucketMapper;
import com.lumora.cloud.billing.utils.BillingAmounts;
import com.lumora.cloud.billing.utils.QuotaCycleCalculator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class QuotaBucketService {

    private final QuotaCycleCalculator cycleCalculator;
    private final PlanVersionMapper versionMapper;
    private final QuotaBucketMapper bucketMapper;
    private final QuotaLedgerWriter ledgerWriter;

    public QuotaBucketService(
            QuotaCycleCalculator cycleCalculator,
            PlanVersionMapper versionMapper,
            QuotaBucketMapper bucketMapper,
            QuotaLedgerWriter ledgerWriter
    ) {
        this.cycleCalculator = cycleCalculator;
        this.versionMapper = versionMapper;
        this.bucketMapper = bucketMapper;
        this.ledgerWriter = ledgerWriter;
    }

    public QuotaBucketEntity currentForUpdate(SubscriptionEntity subscription, Instant now) {
        QuotaCycleCalculator.Cycle cycle = cycleCalculator.current(subscription, now);
        QuotaBucketEntity existing = bucketMapper.findPeriodForUpdate(subscription.getId(), cycle.periodNo());
        if (existing != null) {
            return existing;
        }
        PlanVersionEntity version = versionMapper.selectById(subscription.getPlanVersionId());
        if (version == null) {
            throw new IllegalStateException("Subscription references a missing plan version");
        }
        QuotaBucketEntity bucket = QuotaBucketEntity.create(
                UUID.randomUUID().toString(), subscription.getId(), subscription.getUserId(),
                cycle.periodNo(), cycle.startsAt(), cycle.endsAt(), version.getWeeklyQuota()
        );
        try {
            bucketMapper.insert(bucket);
        } catch (DuplicateKeyException exception) {
            QuotaBucketEntity concurrent = bucketMapper.findPeriodForUpdate(subscription.getId(), cycle.periodNo());
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
        ledgerWriter.append(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), subscription.getUserId(), bucket.getId(), null,
                LedgerEntryType.GRANT.name(), "QUOTA_BUCKET", bucket.getId(),
                bucket.getGrantedQuota(), BillingAmounts.zero(), BillingAmounts.zero(),
                "创建第 " + cycle.periodNo() + " 个周额度周期"
        ));
        return bucket;
    }
}
