package com.lumora.cloud.billing.domain.vo.reconciliation;

import com.lumora.cloud.billing.domain.entity.quota.ReservationEntity;
import com.lumora.cloud.billing.domain.entity.quota.QuotaLedgerEntity;
import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import java.util.List;

public record ReconciliationCase(ReservationEntity reservation, UsageRecordEntity usage, List<QuotaLedgerEntity> history) {
    public ReconciliationCase(ReservationEntity reservation, UsageRecordEntity usage) {
        this(reservation, usage, List.of());
    }
}
