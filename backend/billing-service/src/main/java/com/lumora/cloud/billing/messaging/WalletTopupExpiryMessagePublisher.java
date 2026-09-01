package com.lumora.cloud.billing.messaging;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;

@Component
public class WalletTopupExpiryMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(WalletTopupExpiryMessagePublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public WalletTopupExpiryMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(WalletTopupExpiryScheduledEvent event) {
        long delayMillis = Math.max(1L, Duration.between(Instant.now(), event.expiresAt()).toMillis());
        try {
            rabbitTemplate.convertAndSend(
                    BillingMessagingConfiguration.ORDER_EXCHANGE,
                    BillingMessagingConfiguration.TOPUP_EXPIRY_DELAY_ROUTING_KEY,
                    event.orderNo(),
                    message -> {
                        message.getMessageProperties().setMessageId("topup:" + event.orderNo());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setExpiration(Long.toString(delayMillis));
                        return message;
                    }
            );
        } catch (RuntimeException exception) {
            log.error("Could not schedule wallet top-up expiry for {}; database scan will recover it",
                    event.orderNo(), exception);
        }
    }
}
