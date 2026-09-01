package com.lumora.cloud.modelgateway.web;

import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticRecord;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsProperties;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsStore;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class ModelGatewayAdminController {

    private final ModelGatewayAccess access;
    private final GatewayDiagnosticsStore diagnostics;
    private final GatewayDiagnosticsProperties properties;

    public ModelGatewayAdminController(
            ModelGatewayAccess access,
            GatewayDiagnosticsStore diagnostics,
            GatewayDiagnosticsProperties properties
    ) {
        this.access = access;
        this.diagnostics = diagnostics;
        this.properties = properties;
    }

    @GetMapping("/diagnostics")
    public Mono<GatewayDiagnosticsResponse> diagnostics(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) {
        access.requireAdmin(exchange.getRequest().getHeaders());
        return Mono.zip(diagnostics.summaryWindow(), diagnostics.recent(limit).collectList())
                .map(tuple -> response(tuple.getT1(), tuple.getT2()));
    }

    private GatewayDiagnosticsResponse response(
            List<GatewayDiagnosticRecord> window, List<GatewayDiagnosticRecord> records
    ) {
        long succeeded = window.stream().filter(record -> "SUCCEEDED".equals(record.status())).count();
        long failed = window.stream().filter(record -> "FAILED".equals(record.status())).count();
        long running = window.stream().filter(record -> "RUNNING".equals(record.status())).count();
        long[] durations = window.stream()
                .filter(record -> record.completedAt() != null)
                .mapToLong(GatewayDiagnosticRecord::durationMillis).sorted().toArray();
        long average = durations.length == 0 ? 0 : Math.round(
                java.util.Arrays.stream(durations).average().orElse(0D)
        );
        long p95 = durations.length == 0 ? 0 : durations[(int) Math.ceil(durations.length * 0.95D) - 1];
        GatewayDiagnosticsSummary summary = new GatewayDiagnosticsSummary(
                window.size(), succeeded, failed, running, average, p95,
                properties.getSummaryWindow().toString(), Instant.now()
        );
        return new GatewayDiagnosticsResponse(summary, records);
    }

    public record GatewayDiagnosticsSummary(
            long total, long succeeded, long failed, long running,
            long averageDurationMillis, long p95DurationMillis,
            String window, Instant generatedAt
    ) {
    }

    public record GatewayDiagnosticsResponse(
            GatewayDiagnosticsSummary summary, List<GatewayDiagnosticRecord> records
    ) {
        public GatewayDiagnosticsResponse {
            records = List.copyOf(records);
        }
    }
}
