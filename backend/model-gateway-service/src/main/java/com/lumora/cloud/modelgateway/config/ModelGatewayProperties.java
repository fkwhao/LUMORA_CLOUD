package com.lumora.cloud.modelgateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("lumora.model-gateway")
public record ModelGatewayProperties(
        @Valid @NotNull Catalog catalog,
        @Valid @NotNull Concurrency concurrency,
        @Valid @NotNull Provider provider,
        @Valid @NotNull Recovery recovery
) {
    public ModelGatewayProperties {
        requirePositive(catalog == null ? null : catalog.localCacheTtl(), "catalog.local-cache-ttl");
        requirePositive(concurrency == null ? null : concurrency.leaseTtl(), "concurrency.lease-ttl");
        requirePositive(concurrency == null ? null : concurrency.requestLeaseTtl(), "concurrency.request-lease-ttl");
        requirePositive(provider == null ? null : provider.connectTimeout(), "provider.connect-timeout");
        requirePositive(provider == null ? null : provider.responseTimeout(), "provider.response-timeout");
        requirePositive(provider == null ? null : provider.maxCallDuration(), "provider.max-call-duration");
        requirePositive(recovery == null ? null : recovery.retention(), "recovery.retention");
        requirePositive(recovery == null ? null : recovery.scanInterval(), "recovery.scan-interval");
        if (concurrency != null && provider != null
                && concurrency.leaseTtl().compareTo(provider.maxCallDuration()) <= 0) {
            throw new IllegalArgumentException("concurrency.lease-ttl must be longer than provider.max-call-duration");
        }
        if (concurrency != null && provider != null
                && concurrency.requestLeaseTtl().compareTo(provider.maxCallDuration()) <= 0) {
            throw new IllegalArgumentException(
                    "concurrency.request-lease-ttl must be longer than provider.max-call-duration"
            );
        }
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }

    public record Catalog(
            @NotNull Duration localCacheTtl,
            @Min(1) @Max(10_000) long maximumSize
    ) {
    }

    public record Concurrency(
            @Min(1) @Max(100) int perUser,
            @Min(1) @Max(10_000) int perModel,
            @NotNull Duration leaseTtl,
            @NotNull Duration requestLeaseTtl
    ) {
    }

    public record Provider(
            @NotNull Duration connectTimeout,
            @NotNull Duration responseTimeout,
            @NotNull Duration maxCallDuration,
            @Min(1) @Max(10_000) int maxConnections,
            @Min(1) @Max(100_000) int pendingAcquireMaxCount,
            @NotNull Duration pendingAcquireTimeout,
            @NotNull Duration maxIdleTime,
            @Min(1_024) @Max(16_777_216) int maxErrorBodyBytes,
            @Min(1_024) @Max(33_554_432) int maxBufferedResponseBytes
    ) {
    }

    public record Recovery(
            @NotNull Duration retention,
            @NotNull Duration scanInterval,
            @Min(1) @Max(1_000) int batchSize
    ) {
    }
}
