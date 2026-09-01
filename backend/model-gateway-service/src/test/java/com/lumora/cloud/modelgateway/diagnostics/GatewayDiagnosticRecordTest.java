package com.lumora.cloud.modelgateway.diagnostics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDiagnosticRecordTest {

    @Test
    void completionOnlyAddsSanitizedOperationalMetadata() {
        Instant startedAt = Instant.parse("2026-09-01T08:00:00Z");
        GatewayDiagnosticRecord running = new GatewayDiagnosticRecord(
                "trace", "client-request", 42L, "coding-model", "",
                "OPENAI_COMPATIBLE", true, "RUNNING", null, null, 0L, startedAt, null
        );

        GatewayDiagnosticRecord completed = running.withCompletion(
                "provider-a", "SUCCEEDED", 200, null, startedAt.plusMillis(1250)
        );

        assertThat(completed.durationMillis()).isEqualTo(1250);
        assertThat(completed.providerCode()).isEqualTo("provider-a");
        assertThat(completed.status()).isEqualTo("SUCCEEDED");
        assertThat(completed.upstreamStatus()).isEqualTo(200);
    }
}
