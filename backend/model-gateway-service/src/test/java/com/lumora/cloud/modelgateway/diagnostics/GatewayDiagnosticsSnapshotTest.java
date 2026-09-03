package com.lumora.cloud.modelgateway.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDiagnosticsSnapshotTest {

    @Test
    void mergesTimeBucketsWithoutARecordCountCap() {
        GatewayDiagnosticsSnapshot first = GatewayDiagnosticsSnapshot.from(values(
                800L, 700L, 50L, 10L, 40L, 760L, 760_000L, 760L, 4
        ));
        GatewayDiagnosticsSnapshot second = GatewayDiagnosticsSnapshot.from(values(
                900L, 850L, 20L, 5L, 25L, 875L, 1_750_000L, 875L, 6
        ));

        GatewayDiagnosticsSnapshot merged = first.plus(second);

        assertThat(merged.total()).isEqualTo(1_700L);
        assertThat(merged.succeeded()).isEqualTo(1_550L);
        assertThat(merged.failed()).isEqualTo(70L);
        assertThat(merged.canceled()).isEqualTo(15L);
        assertThat(merged.running()).isEqualTo(65L);
        assertThat(merged.averageDurationMillis()).isEqualTo(1_535L);
        assertThat(merged.p95DurationMillis()).isEqualTo(5_000L);
    }

    private Map<String, String> values(
            long total,
            long succeeded,
            long failed,
            long canceled,
            long running,
            long durationCount,
            long durationTotal,
            long histogramCount,
            int histogramIndex
    ) {
        Map<String, String> values = new HashMap<>();
        values.put("total", Long.toString(total));
        values.put("succeeded", Long.toString(succeeded));
        values.put("failed", Long.toString(failed));
        values.put("canceled", Long.toString(canceled));
        values.put("running", Long.toString(running));
        values.put("duration_count", Long.toString(durationCount));
        values.put("duration_total_millis", Long.toString(durationTotal));
        values.put("duration_bucket_" + histogramIndex, Long.toString(histogramCount));
        return values;
    }
}
