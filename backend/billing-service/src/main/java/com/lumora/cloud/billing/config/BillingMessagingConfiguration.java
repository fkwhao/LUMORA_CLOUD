package com.lumora.cloud.billing.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingMessagingConfiguration {

    public static final String ORDER_EXCHANGE = "lumora.billing.order";
    public static final String ORDER_EXPIRY_DELAY_QUEUE = "lumora.billing.order.expiry.delay.q";
    public static final String ORDER_EXPIRY_QUEUE = "lumora.billing.order.expiry.q";
    public static final String ORDER_EXPIRY_DELAY_ROUTING_KEY = "order.expire.delay";
    public static final String ORDER_EXPIRY_ROUTING_KEY = "order.expire";

    public static final String FAILED_EXCHANGE = "lumora.billing.failed";
    public static final String ORDER_EXPIRY_FAILED_QUEUE = "lumora.billing.order.expiry.failed.q";
    public static final String ORDER_EXPIRY_FAILED_ROUTING_KEY = "order.expire.failed";

    public static final String TOPUP_EXPIRY_DELAY_QUEUE = "lumora.billing.wallet.topup.expiry.delay.q";
    public static final String TOPUP_EXPIRY_QUEUE = "lumora.billing.wallet.topup.expiry.q";
    public static final String TOPUP_EXPIRY_DELAY_ROUTING_KEY = "wallet.topup.expire.delay";
    public static final String TOPUP_EXPIRY_ROUTING_KEY = "wallet.topup.expire";
    public static final String TOPUP_EXPIRY_FAILED_QUEUE = "lumora.billing.wallet.topup.expiry.failed.q";
    public static final String TOPUP_EXPIRY_FAILED_ROUTING_KEY = "wallet.topup.expire.failed";

    @Bean
    DirectExchange billingOrderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue orderExpiryDelayQueue() {
        return QueueBuilder.durable(ORDER_EXPIRY_DELAY_QUEUE)
                .deadLetterExchange(ORDER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_EXPIRY_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding orderExpiryDelayBinding(DirectExchange billingOrderExchange) {
        return BindingBuilder.bind(orderExpiryDelayQueue())
                .to(billingOrderExchange)
                .with(ORDER_EXPIRY_DELAY_ROUTING_KEY);
    }

    @Bean
    Queue orderExpiryQueue() {
        return QueueBuilder.durable(ORDER_EXPIRY_QUEUE)
                .deadLetterExchange(FAILED_EXCHANGE)
                .deadLetterRoutingKey(ORDER_EXPIRY_FAILED_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding orderExpiryBinding(DirectExchange billingOrderExchange) {
        return BindingBuilder.bind(orderExpiryQueue())
                .to(billingOrderExchange)
                .with(ORDER_EXPIRY_ROUTING_KEY);
    }

    @Bean
    DirectExchange billingFailedExchange() {
        return ExchangeBuilder.directExchange(FAILED_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue orderExpiryFailedQueue() {
        return QueueBuilder.durable(ORDER_EXPIRY_FAILED_QUEUE).build();
    }

    @Bean
    Binding orderExpiryFailedBinding(DirectExchange billingFailedExchange) {
        return BindingBuilder.bind(orderExpiryFailedQueue())
                .to(billingFailedExchange)
                .with(ORDER_EXPIRY_FAILED_ROUTING_KEY);
    }

    @Bean
    Queue topupExpiryDelayQueue() {
        return QueueBuilder.durable(TOPUP_EXPIRY_DELAY_QUEUE)
                .deadLetterExchange(ORDER_EXCHANGE)
                .deadLetterRoutingKey(TOPUP_EXPIRY_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding topupExpiryDelayBinding(DirectExchange billingOrderExchange) {
        return BindingBuilder.bind(topupExpiryDelayQueue())
                .to(billingOrderExchange).with(TOPUP_EXPIRY_DELAY_ROUTING_KEY);
    }

    @Bean
    Queue topupExpiryQueue() {
        return QueueBuilder.durable(TOPUP_EXPIRY_QUEUE)
                .deadLetterExchange(FAILED_EXCHANGE)
                .deadLetterRoutingKey(TOPUP_EXPIRY_FAILED_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding topupExpiryBinding(DirectExchange billingOrderExchange) {
        return BindingBuilder.bind(topupExpiryQueue())
                .to(billingOrderExchange).with(TOPUP_EXPIRY_ROUTING_KEY);
    }

    @Bean
    Queue topupExpiryFailedQueue() {
        return QueueBuilder.durable(TOPUP_EXPIRY_FAILED_QUEUE).build();
    }

    @Bean
    Binding topupExpiryFailedBinding(DirectExchange billingFailedExchange) {
        return BindingBuilder.bind(topupExpiryFailedQueue())
                .to(billingFailedExchange).with(TOPUP_EXPIRY_FAILED_ROUTING_KEY);
    }

    @Bean("orderExpiryListenerContainerFactory")
    SimpleRabbitListenerContainerFactory orderExpiryListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
