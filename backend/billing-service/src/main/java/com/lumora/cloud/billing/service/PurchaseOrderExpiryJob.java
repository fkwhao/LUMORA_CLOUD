package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.persistence.mapper.PurchaseOrderMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PurchaseOrderExpiryJob {

    private final PurchaseOrderMapper orderMapper;

    public PurchaseOrderExpiryJob(PurchaseOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Scheduled(
            fixedDelayString = "${lumora.billing.payment.expiry-scan-interval:PT1M}",
            initialDelayString = "${lumora.billing.payment.expiry-initial-delay:PT1M}"
    )
    public void expirePendingOrders() {
        orderMapper.expirePending(Instant.now(), 1000);
    }
}
