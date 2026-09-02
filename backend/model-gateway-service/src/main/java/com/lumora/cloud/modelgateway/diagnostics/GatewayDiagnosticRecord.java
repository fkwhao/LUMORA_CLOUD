package com.lumora.cloud.modelgateway.diagnostics;

import java.time.Instant;

public record GatewayDiagnosticRecord(
        String traceId,
        String clientRequestId,
        long userId,
        String modelCode,
        String providerCode,
        String routeId,
        String routeName,
        String protocol,
        boolean stream,
        String status,
        Integer upstreamStatus,
        String errorCode,
        long durationMillis,
        Instant startedAt,
        Instant completedAt
) {
    public GatewayDiagnosticRecord(
            String traceId, String clientRequestId, long userId, String modelCode,
            String providerCode, String protocol, boolean stream, String status,
            Integer upstreamStatus, String errorCode, long durationMillis,
            Instant startedAt, Instant completedAt
    ) {
        this(traceId, clientRequestId, userId, modelCode, providerCode, "", "", protocol,
                stream, status, upstreamStatus, errorCode, durationMillis, startedAt, completedAt);
    }

    public GatewayDiagnosticRecord withCompletion(
            String providerCode, String status, Integer upstreamStatus,
            String errorCode, Instant completedAt
    ) {
        return withCompletion(providerCode, "", "", status, upstreamStatus, errorCode, completedAt);
    }

    public GatewayDiagnosticRecord withCompletion(
            String providerCode, String routeId, String routeName, String status, Integer upstreamStatus,
            String errorCode, Instant completedAt
    ) {
        long elapsed = Math.max(0L, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        return new GatewayDiagnosticRecord(
                traceId, clientRequestId, userId, modelCode,
                providerCode == null || providerCode.isBlank() ? this.providerCode : providerCode,
                routeId == null || routeId.isBlank() ? this.routeId : routeId,
                routeName == null || routeName.isBlank() ? this.routeName : routeName,
                protocol, stream, status, upstreamStatus, errorCode, elapsed, startedAt, completedAt
        );
    }
}
