package com.lumora.cloud.modelgateway.controller.admin;

import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticRecord;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsSnapshot;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsProperties;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsStore;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import com.lumora.cloud.modelgateway.domain.vo.diagnostics.GatewayDiagnosticsResponse;
import com.lumora.cloud.modelgateway.domain.vo.diagnostics.GatewayDiagnosticsSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/model-gateway")
@RequiredArgsConstructor
public class ModelGatewayAdminController {

    private final ModelGatewayAccess access;
    private final GatewayDiagnosticsStore diagnostics;
    private final GatewayDiagnosticsProperties properties;

    @GetMapping("/diagnostics")
    public Mono<GatewayDiagnosticsResponse> diagnostics(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit
    ) {
        access.requireAdmin(exchange.getRequest().getHeaders());
        return Mono.zip(diagnostics.summaryWindow(), diagnostics.recent(limit).collectList())
                .map(tuple -> response(tuple.getT1(), tuple.getT2()));
    }

    private GatewayDiagnosticsResponse response(
            GatewayDiagnosticsSnapshot window, List<GatewayDiagnosticRecord> records
    ) {
        GatewayDiagnosticsSummary summary = new GatewayDiagnosticsSummary(
                window.total(), window.succeeded(), window.failed(), window.canceled(), window.running(),
                window.averageDurationMillis(), window.p95DurationMillis(),
                properties.getSummaryWindow().toString(), Instant.now()
        );
        return new GatewayDiagnosticsResponse(summary, records);
    }

}
