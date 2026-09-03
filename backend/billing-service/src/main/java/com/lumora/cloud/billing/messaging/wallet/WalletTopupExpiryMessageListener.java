package com.lumora.cloud.billing.messaging.wallet;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import com.lumora.cloud.billing.service.IWalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WalletTopupExpiryMessageListener {

    private static final Logger log = LoggerFactory.getLogger(WalletTopupExpiryMessageListener.class);
    private final IWalletService walletService;

    public WalletTopupExpiryMessageListener(IWalletService walletService) {
        this.walletService = walletService;
    }

    @RabbitListener(
            queues = BillingMessagingConfiguration.TOPUP_EXPIRY_QUEUE,
            containerFactory = "orderExpiryListenerContainerFactory"
    )
    public void expire(String orderNo) {
        if (walletService.expireTopup(orderNo, Instant.now())) {
            log.info("Expired unpaid wallet top-up {} from RabbitMQ delay queue", orderNo);
        }
    }
}
