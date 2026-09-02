package com.lumora.cloud.modelgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@ConfigurationProperties("lumora.model-gateway.route-protection")
@Validated
public class RouteProtectionProperties {

    @DecimalMin("0.01")
    @DecimalMax("1.0")
    private double failureRatio = 0.5D;
    @Min(1)
    private int minimumCalls = 10;
    @Min(1)
    private int statisticalWindowSeconds = 30;
    @Min(1)
    private int openDurationSeconds = 10;

    public double getFailureRatio() {
        return failureRatio;
    }

    public void setFailureRatio(double failureRatio) {
        this.failureRatio = failureRatio;
    }

    public int getMinimumCalls() {
        return minimumCalls;
    }

    public void setMinimumCalls(int minimumCalls) {
        this.minimumCalls = minimumCalls;
    }

    public int getStatisticalWindowSeconds() {
        return statisticalWindowSeconds;
    }

    public void setStatisticalWindowSeconds(int statisticalWindowSeconds) {
        this.statisticalWindowSeconds = statisticalWindowSeconds;
    }

    public int getOpenDurationSeconds() {
        return openDurationSeconds;
    }

    public void setOpenDurationSeconds(int openDurationSeconds) {
        this.openDurationSeconds = openDurationSeconds;
    }
}
