package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.persistence.entity.SubscriptionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaCycleCalculatorTest {

    private final QuotaCycleCalculator calculator = new QuotaCycleCalculator();
    private final Instant startsAt = Instant.parse("2026-08-01T02:00:00Z");
    private final SubscriptionEntity subscription = SubscriptionEntity.grant(
            "subscription-id", 1L, 2L, 3L, "grant-reference",
            startsAt, startsAt.plusSeconds(30L * 24 * 60 * 60)
    );

    @Test
    void startsASevenDayCycleAtSubscriptionActivation() {
        QuotaCycleCalculator.Cycle cycle = calculator.current(subscription, startsAt);

        assertThat(cycle.periodNo()).isEqualTo(1);
        assertThat(cycle.startsAt()).isEqualTo(startsAt);
        assertThat(cycle.endsAt()).isEqualTo(startsAt.plusSeconds(7L * 24 * 60 * 60));
    }

    @Test
    void movesToNextCycleExactlyAfterSevenDays() {
        Instant secondCycleStart = startsAt.plusSeconds(7L * 24 * 60 * 60);

        QuotaCycleCalculator.Cycle cycle = calculator.current(subscription, secondCycleStart);

        assertThat(cycle.periodNo()).isEqualTo(2);
        assertThat(cycle.startsAt()).isEqualTo(secondCycleStart);
        assertThat(cycle.endsAt()).isEqualTo(startsAt.plusSeconds(14L * 24 * 60 * 60));
    }

    @Test
    void capsLastCycleAtSubscriptionEnd() {
        QuotaCycleCalculator.Cycle cycle = calculator.current(
                subscription,
                startsAt.plusSeconds(29L * 24 * 60 * 60)
        );

        assertThat(cycle.periodNo()).isEqualTo(5);
        assertThat(cycle.endsAt()).isEqualTo(subscription.getEndsAt());
    }

    @Test
    void rejectsTimesOutsideSubscription() {
        assertThatThrownBy(() -> calculator.current(subscription, subscription.getEndsAt()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
