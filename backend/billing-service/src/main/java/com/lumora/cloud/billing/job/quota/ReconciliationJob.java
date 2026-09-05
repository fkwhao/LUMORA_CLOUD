package com.lumora.cloud.billing.job.quota;

import com.lumora.cloud.billing.mapper.quota.ReservationMapper;
import com.lumora.cloud.billing.service.ISettlementService;
import com.lumora.cloud.billing.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "lumora.billing.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private final ReservationMapper reservations;
    private final ISettlementService settlements;
    private final int batchSize;

    public ReconciliationJob(ReservationMapper reservations, ISettlementService settlements,
            @Value("${lumora.billing.reconciliation.batch-size:100}") int batchSize) {
        this.reservations = reservations;
        this.settlements = settlements;
        this.batchSize = Math.max(1, Math.min(1000, batchSize));
    }

    @Scheduled(fixedDelayString = "${lumora.billing.reconciliation.scan-interval:PT1M}",
               initialDelayString = "${lumora.billing.reconciliation.initial-delay:PT1M}")
    public void reconcileDue() {
        for (String id : reservations.findDueReconciliation(Instant.now(), batchSize)) {
            try {
                settlements.autoReconcile(id);
            } catch (RuntimeException error) {
                log.warn("Reconciliation failed for request {}", id, error);
                try {
                    settlements.recordReconciliationFailure(id, error instanceof ApiException api
                            ? api.getCode() : "自动核对暂时失败，请稍后重试");
                } catch (RuntimeException recordError) {
                    log.error("Could not record reconciliation failure for {}", id, recordError);
                }
            }
        }
    }
}
