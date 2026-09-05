package com.lumora.cloud.billing.job.quota;

import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.mapper.quota.ReservationMapper;
import com.lumora.cloud.billing.service.ISettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "lumora.billing.reservation-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationMapper reservationMapper;
    private final ISettlementService settlementService;
    private final int batchSize;

    public ReservationExpiryJob(
            ReservationMapper reservationMapper,
            ISettlementService settlementService,
            @Value("${lumora.billing.reservation-expiry.batch-size:100}") int batchSize
    ) {
        this.reservationMapper = reservationMapper;
        this.settlementService = settlementService;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
    }

    @Scheduled(
            fixedDelayString = "${lumora.billing.reservation-expiry.scan-interval:PT1M}",
            initialDelayString = "${lumora.billing.reservation-expiry.initial-delay:PT1M}"
    )
    public void releaseExpiredReservations() {
        List<String> requestIds = reservationMapper.findExpiredActiveRequestIds(Instant.now(), batchSize);
        for (String requestId : requestIds) {
            try {
                settlementService.expire(requestId);
            } catch (ApiException exception) {
                log.debug("Expired reservation {} changed before release: {}", requestId, exception.getCode());
            } catch (RuntimeException exception) {
                log.error("Failed to release expired reservation {}", requestId, exception);
            }
        }
    }
}
