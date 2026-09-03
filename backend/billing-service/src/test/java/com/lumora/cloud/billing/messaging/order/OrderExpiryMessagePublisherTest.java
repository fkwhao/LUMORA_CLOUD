package com.lumora.cloud.billing.messaging.order;

import com.lumora.cloud.billing.config.BillingMessagingConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderExpiryMessagePublisherTest {

    @Test
    void publishesPersistentDelayedMessageAfterOrderCommit() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OrderExpiryMessagePublisher publisher = new OrderExpiryMessagePublisher(rabbitTemplate);
        String orderNo = "LU20260901070000ABCDEFGHIJKL";

        publisher.publish(new OrderExpiryScheduledEvent(orderNo, Instant.now().plusSeconds(120)));

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(BillingMessagingConfiguration.ORDER_EXCHANGE),
                eq(BillingMessagingConfiguration.ORDER_EXPIRY_DELAY_ROUTING_KEY),
                eq(orderNo),
                processor.capture()
        );
        Message message = processor.getValue().postProcessMessage(new Message(new byte[0]));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(orderNo);
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(Long.parseLong(message.getMessageProperties().getExpiration())).isPositive();
    }
}
