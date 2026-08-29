package com.lumora.cloud.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("lumora.billing.payment")
public record PaymentProperties(
        boolean mockEnabled,
        Duration orderTtl,
        Duration subscriptionDuration
) {
    public PaymentProperties {
        orderTtl = orderTtl == null ? Duration.ofMinutes(30) : orderTtl;
        subscriptionDuration = subscriptionDuration == null ? Duration.ofDays(30) : subscriptionDuration;
        if (orderTtl.isNegative() || orderTtl.isZero()) {
            throw new IllegalArgumentException("lumora.billing.payment.order-ttl must be positive");
        }
        if (subscriptionDuration.isNegative() || subscriptionDuration.isZero()) {
            throw new IllegalArgumentException("lumora.billing.payment.subscription-duration must be positive");
        }
    }
}
