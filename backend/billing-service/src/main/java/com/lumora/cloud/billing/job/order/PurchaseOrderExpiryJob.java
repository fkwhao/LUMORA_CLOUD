package com.lumora.cloud.billing.job.order;

import com.lumora.cloud.billing.mapper.order.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PurchaseOrderExpiryJob {

    private final PurchaseOrderMapper orderMapper;

    @Scheduled(
            fixedDelayString = "${lumora.billing.payment.expiry-scan-interval:PT1M}",
            initialDelayString = "${lumora.billing.payment.expiry-initial-delay:PT1M}"
    )
    public void expirePendingOrders() {
        orderMapper.expirePending(Instant.now(), 1000);
    }
}
