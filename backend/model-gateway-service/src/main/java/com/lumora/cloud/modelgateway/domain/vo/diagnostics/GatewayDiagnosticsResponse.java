package com.lumora.cloud.modelgateway.domain.vo.diagnostics;

import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticRecord;

import java.util.List;

public record GatewayDiagnosticsResponse(
        GatewayDiagnosticsSummary summary,
        List<GatewayDiagnosticRecord> records
) {
    public GatewayDiagnosticsResponse {
        records = List.copyOf(records);
    }
}
