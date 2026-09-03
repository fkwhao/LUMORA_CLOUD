package com.lumora.cloud.modelgateway.diagnostics;

import java.util.Arrays;
import java.util.Map;

public final class GatewayDiagnosticsSnapshot {

    private static final long[] DURATION_UPPER_BOUNDS_MILLIS = {
            50L, 100L, 250L, 500L, 1_000L, 2_000L, 5_000L,
            10_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L
    };

    private final long total;
    private final long succeeded;
    private final long failed;
    private final long canceled;
    private final long running;
    private final long durationCount;
    private final long durationTotalMillis;
    private final long[] durationHistogram;

    public GatewayDiagnosticsSnapshot(
            long total,
            long succeeded,
            long failed,
            long canceled,
            long running,
            long durationCount,
            long durationTotalMillis,
            long[] durationHistogram
    ) {
        this.total = total;
        this.succeeded = succeeded;
        this.failed = failed;
        this.canceled = canceled;
        this.running = Math.max(0L, running);
        this.durationCount = durationCount;
        this.durationTotalMillis = durationTotalMillis;
        this.durationHistogram = Arrays.copyOf(durationHistogram, DURATION_UPPER_BOUNDS_MILLIS.length);
    }

    public static GatewayDiagnosticsSnapshot empty() {
        return new GatewayDiagnosticsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L,
                new long[DURATION_UPPER_BOUNDS_MILLIS.length]);
    }

    public static GatewayDiagnosticsSnapshot from(Map<String, String> values) {
        long[] histogram = new long[DURATION_UPPER_BOUNDS_MILLIS.length];
        for (int index = 0; index < histogram.length; index++) {
            histogram[index] = number(values.get(histogramField(index)));
        }
        return new GatewayDiagnosticsSnapshot(
                number(values.get("total")),
                number(values.get("succeeded")),
                number(values.get("failed")),
                number(values.get("canceled")),
                number(values.get("running")),
                number(values.get("duration_count")),
                number(values.get("duration_total_millis")),
                histogram
        );
    }

    public GatewayDiagnosticsSnapshot plus(GatewayDiagnosticsSnapshot other) {
        long[] merged = new long[durationHistogram.length];
        for (int index = 0; index < merged.length; index++) {
            merged[index] = durationHistogram[index] + other.durationHistogram[index];
        }
        return new GatewayDiagnosticsSnapshot(
                total + other.total,
                succeeded + other.succeeded,
                failed + other.failed,
                canceled + other.canceled,
                running + other.running,
                durationCount + other.durationCount,
                durationTotalMillis + other.durationTotalMillis,
                merged
        );
    }

    public static String histogramField(long durationMillis) {
        for (int index = 0; index < DURATION_UPPER_BOUNDS_MILLIS.length; index++) {
            if (durationMillis <= DURATION_UPPER_BOUNDS_MILLIS[index]) {
                return histogramField(index);
            }
        }
        return histogramField(DURATION_UPPER_BOUNDS_MILLIS.length - 1);
    }

    public long averageDurationMillis() {
        return durationCount == 0L ? 0L : Math.round((double) durationTotalMillis / durationCount);
    }

    public long p95DurationMillis() {
        if (durationCount == 0L) {
            return 0L;
        }
        long target = (long) Math.ceil(durationCount * 0.95D);
        long cumulative = 0L;
        for (int index = 0; index < durationHistogram.length; index++) {
            cumulative += durationHistogram[index];
            if (cumulative >= target) {
                return DURATION_UPPER_BOUNDS_MILLIS[index];
            }
        }
        return DURATION_UPPER_BOUNDS_MILLIS[DURATION_UPPER_BOUNDS_MILLIS.length - 1];
    }

    public long total() { return total; }
    public long succeeded() { return succeeded; }
    public long failed() { return failed; }
    public long canceled() { return canceled; }
    public long running() { return running; }

    private static String histogramField(int index) {
        return "duration_bucket_" + index;
    }

    private static long number(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
