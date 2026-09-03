package com.lumora.cloud.billing.utils;

import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class QuotaCycleCalculator {

    static final Duration PERIOD = Duration.ofDays(7);

    public Cycle current(SubscriptionEntity subscription, Instant now) {
        if (now.isBefore(subscription.getStartsAt()) || !now.isBefore(subscription.getEndsAt())) {
            throw new IllegalArgumentException("Subscription is not active at the requested time");
        }
        long elapsedSeconds = Duration.between(subscription.getStartsAt(), now).getSeconds();
        long periodIndex = elapsedSeconds / PERIOD.getSeconds();
        if (periodIndex > Integer.MAX_VALUE - 1L) {
            throw new IllegalStateException("Subscription period exceeds supported range");
        }
        Instant startsAt = subscription.getStartsAt().plus(PERIOD.multipliedBy(periodIndex));
        Instant naturalEnd = startsAt.plus(PERIOD);
        Instant endsAt = naturalEnd.isBefore(subscription.getEndsAt()) ? naturalEnd : subscription.getEndsAt();
        return new Cycle((int) periodIndex + 1, startsAt, endsAt);
    }

    public record Cycle(int periodNo, Instant startsAt, Instant endsAt) {
    }
}
