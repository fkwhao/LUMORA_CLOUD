package com.lumora.cloud.billing.support;

import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.mapper.quota.QuotaDailySummaryMapper;
import com.lumora.cloud.billing.mapper.quota.QuotaLedgerMapper;
import com.lumora.cloud.billing.utils.BillingReportingPeriods;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class QuotaLedgerWriter {

    private final QuotaLedgerMapper ledgerMapper;
    private final QuotaDailySummaryMapper dailySummaryMapper;

    public QuotaLedgerWriter(
            QuotaLedgerMapper ledgerMapper,
            QuotaDailySummaryMapper dailySummaryMapper
    ) {
        this.ledgerMapper = ledgerMapper;
        this.dailySummaryMapper = dailySummaryMapper;
    }

    public void append(QuotaLedgerEntity entry) {
        if (ledgerMapper.insert(entry) != 1) {
            throw new IllegalStateException("Quota ledger entry could not be appended");
        }
        Instant occurredAt = entry.getCreatedAt() == null ? Instant.now() : entry.getCreatedAt();
        if (dailySummaryMapper.addEntry(entry, BillingReportingPeriods.localDate(occurredAt)) <= 0) {
            throw new IllegalStateException("Quota daily summary could not be updated");
        }
    }
}
