package com.lumora.cloud.billing.utils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class BillingReportingPeriods {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BillingReportingPeriods() {
    }

    public static LocalDate localDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static DateRange currentMonth(Instant now) {
        LocalDate startsOn = localDate(now).withDayOfMonth(1);
        return new DateRange(startsOn, startsOn.plusMonths(1));
    }

    public static DateRange weekContaining(LocalDate anchor) {
        LocalDate startsOn = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DateRange(startsOn, startsOn.plusDays(7));
    }

    public static DateRange monthContaining(LocalDate anchor) {
        LocalDate startsOn = anchor.withDayOfMonth(1);
        return new DateRange(startsOn, startsOn.plusMonths(1));
    }

    public record DateRange(LocalDate startsOn, LocalDate endsOnExclusive) {
        public Instant startsAt() {
            return startOfDay(startsOn);
        }

        public Instant endsAt() {
            return startOfDay(endsOnExclusive);
        }
    }
}
