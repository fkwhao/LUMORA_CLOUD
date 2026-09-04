package com.lumora.cloud.billing.messaging.order;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OrderExpiryMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderExpiryScheduledEvent event) {
        long delayMillis = Math.max(1L, Duration.between(Instant.now(), event.expiresAt()).toMillis());
        try {
            rabbitTemplate.convertAndSend(
                    BillingMessagingConfiguration.ORDER_EXCHANGE,
                    BillingMessagingConfiguration.ORDER_EXPIRY_DELAY_ROUTING_KEY,
                    event.orderNo(),
                    message -> {
                        message.getMessageProperties().setMessageId(event.orderNo());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setExpiration(Long.toString(delayMillis));
                        return message;
                    }
            );
        } catch (RuntimeException exception) {
            // The database expiry scanner remains authoritative when RabbitMQ is temporarily unavailable.
            log.error("Could not schedule RabbitMQ expiry message for order {}; database scan will recover it",
                    event.orderNo(), exception);
        }
    }
}
