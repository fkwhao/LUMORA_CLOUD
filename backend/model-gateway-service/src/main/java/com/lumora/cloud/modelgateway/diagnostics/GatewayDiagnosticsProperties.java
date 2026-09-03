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
    private Duration summaryWindow = Duration.ofHours(24);
    @NotNull
    private Duration summaryBucket = Duration.ofMinutes(5);
    @NotNull
    private Duration bucketRetention = Duration.ofHours(26);
    @NotNull
    private Duration recentRetention = Duration.ofHours(24);
    @Min(1)
    @Max(100)
    private int recentLimit = 100;

    public Duration getSummaryWindow() { return summaryWindow; }
    public void setSummaryWindow(Duration summaryWindow) { this.summaryWindow = summaryWindow; }
    public Duration getSummaryBucket() { return summaryBucket; }
    public void setSummaryBucket(Duration summaryBucket) { this.summaryBucket = summaryBucket; }
    public Duration getBucketRetention() { return bucketRetention; }
    public void setBucketRetention(Duration bucketRetention) { this.bucketRetention = bucketRetention; }
    public Duration getRecentRetention() { return recentRetention; }
    public void setRecentRetention(Duration recentRetention) { this.recentRetention = recentRetention; }
    public int getRecentLimit() { return recentLimit; }
    public void setRecentLimit(int recentLimit) { this.recentLimit = recentLimit; }
}
