package com.lumora.cloud.billing.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillingReportingPeriodsTest {

    @Test
    void usesShanghaiCalendarBoundaries() {
        var month = BillingReportingPeriods.currentMonth(Instant.parse("2026-08-31T16:30:00Z"));

        assertThat(month.startsOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(month.endsOnExclusive()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(month.startsAt()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
    }

    @Test
    void startsWeeksOnMonday() {
        var week = BillingReportingPeriods.weekContaining(LocalDate.of(2026, 9, 3));

        assertThat(week.startsOn()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(week.endsOnExclusive()).isEqualTo(LocalDate.of(2026, 9, 7));
    }
}
