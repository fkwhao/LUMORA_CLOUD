package com.lumora.cloud.api.billing;

import java.math.BigDecimal;
import java.time.Instant;

public final class BillingContracts {

    private BillingContracts() {
    }

    public enum ReservationStatus {
        PROCESSING,
        ACTIVE,
        SETTLED,
        RELEASED,
        PENDING_RECONCILIATION
    }

    public enum UsageStatus {
        PROCESSING,
        COMPLETED,
        PENDING_RECONCILIATION
    }

    public record ReserveRequest(
            String requestId,
            String clientRequestId,
            Long userId,
            String modelCode,
            String pricingVersion,
            BigDecimal maximumQuota,
            Instant expiresAt
    ) {
    }

    public record SettleRequest(
            String usageId,
            String pricingVersion,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            BigDecimal billedQuota,
            Instant occurredAt
    ) {
    }

    public record ReleaseRequest(String reason) {
    }

    public record PendingRequest(String reason) {
    }

    public record ReservationResponse(
            String reservationId,
            String requestId,
            Long userId,
            String modelCode,
            String pricingVersion,
            ReservationStatus status,
            BigDecimal reservedQuota,
            BigDecimal settledQuota,
            BigDecimal remainingQuota,
            Instant expiresAt,
            boolean idempotentReplay
    ) {
    }

    public record SettlementResponse(
            String usageId,
            String requestId,
            ReservationStatus reservationStatus,
            UsageStatus usageStatus,
            BigDecimal reservedQuota,
            BigDecimal billedQuota,
            BigDecimal releasedQuota,
            BigDecimal remainingQuota
    ) {
    }
}
