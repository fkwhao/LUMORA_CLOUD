package com.lumora.cloud.modelgateway.domain.vo.diagnostics;

import java.time.Instant;

public record GatewayDiagnosticsSummary(
        long total,
        long succeeded,
        long failed,
        long canceled,
        long running,
        long averageDurationMillis,
        long p95DurationMillis,
        String window,
        Instant generatedAt
) {
}
