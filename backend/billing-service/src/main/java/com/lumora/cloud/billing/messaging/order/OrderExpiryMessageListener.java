package com.lumora.cloud.billing.messaging.order;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import com.lumora.cloud.billing.service.IPurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OrderExpiryMessageListener {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryMessageListener.class);

    private final IPurchaseOrderService orderService;

    @RabbitListener(
            queues = BillingMessagingConfiguration.ORDER_EXPIRY_QUEUE,
            containerFactory = "orderExpiryListenerContainerFactory"
    )
    public void expire(String orderNo) {
        boolean expired = orderService.expirePending(orderNo, Instant.now());
        if (expired) {
            log.info("Expired unpaid purchase order {} from RabbitMQ delay queue", orderNo);
        } else {
            log.debug("Ignored RabbitMQ expiry message for non-expirable order [{}]", orderNo);
        }
    }
}
