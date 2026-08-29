package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.BillingTypes.LedgerEntryType;
import com.lumora.cloud.billing.persistence.entity.PlanVersionEntity;
import com.lumora.cloud.billing.persistence.entity.QuotaBucketEntity;
import com.lumora.cloud.billing.persistence.entity.QuotaLedgerEntity;
import com.lumora.cloud.billing.persistence.entity.SubscriptionEntity;
import com.lumora.cloud.billing.persistence.mapper.PlanVersionMapper;
import com.lumora.cloud.billing.persistence.mapper.QuotaBucketMapper;
import com.lumora.cloud.billing.persistence.mapper.QuotaLedgerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class QuotaBucketService {

    private final QuotaCycleCalculator cycleCalculator;
    private final PlanVersionMapper versionMapper;
    private final QuotaBucketMapper bucketMapper;
    private final QuotaLedgerMapper ledgerMapper;

    public QuotaBucketService(
            QuotaCycleCalculator cycleCalculator,
            PlanVersionMapper versionMapper,
            QuotaBucketMapper bucketMapper,
            QuotaLedgerMapper ledgerMapper
    ) {
        this.cycleCalculator = cycleCalculator;
        this.versionMapper = versionMapper;
        this.bucketMapper = bucketMapper;
        this.ledgerMapper = ledgerMapper;
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
        ledgerMapper.insert(QuotaLedgerEntity.create(
                UUID.randomUUID().toString(), subscription.getUserId(), bucket.getId(), null,
                LedgerEntryType.GRANT.name(), "QUOTA_BUCKET", bucket.getId(),
                bucket.getGrantedQuota(), BillingAmounts.zero(), BillingAmounts.zero(),
                "创建第 " + cycle.periodNo() + " 个周额度周期"
        ));
        return bucket;
    }
}
