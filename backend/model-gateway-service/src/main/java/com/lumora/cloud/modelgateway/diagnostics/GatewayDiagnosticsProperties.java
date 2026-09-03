package com.lumora.cloud.modelgateway.diagnostics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties("lumora.model-gateway.diagnostics")
public class GatewayDiagnosticsProperties {

    @NotNull
    private Duration retention = Duration.ofDays(7);
    @NotNull
    private Duration summaryWindow = Duration.ofHours(24);
    @NotNull
    private Duration summaryBucket = Duration.ofMinutes(5);
    @Min(100)
    @Max(10_000)
    private int maxRecords = 1_000;

    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
    public Duration getSummaryWindow() { return summaryWindow; }
    public void setSummaryWindow(Duration summaryWindow) { this.summaryWindow = summaryWindow; }
    public Duration getSummaryBucket() { return summaryBucket; }
    public void setSummaryBucket(Duration summaryBucket) { this.summaryBucket = summaryBucket; }
    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
}
