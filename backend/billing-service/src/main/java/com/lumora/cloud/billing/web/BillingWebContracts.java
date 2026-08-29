package com.lumora.cloud.billing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillingWebContracts {

    private BillingWebContracts() {
    }

    public record CreatePlanRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Min(0) long monthlyPriceMinor,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin(value = "0.000001") BigDecimal weeklyQuota
    ) {
    }

    public record GrantSubscriptionRequest(
            @NotNull @Min(1) Long userId,
            @NotNull @Min(1) Long planVersionId,
            @NotBlank @Size(max = 128) String sourceReference,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {
    }

    public record CreatePlanVersionRequest(
            @Min(0) long monthlyPriceMinor,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin(value = "0.000001") BigDecimal weeklyQuota
    ) {
    }

    public record PlanResponse(
            Long planId,
            String code,
            String name,
            String description,
            Long planVersionId,
            int versionNo,
            long monthlyPriceMinor,
            String currency,
            BigDecimal weeklyQuota
    ) {
    }

    public record SubscriptionResponse(
            String subscriptionId,
            Long userId,
            Long planVersionId,
            String status,
            String source,
            String sourceReference,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt
    ) {
    }

    public record QuotaResponse(
            String bucketId,
            int periodNo,
            Instant startsAt,
            Instant endsAt,
            BigDecimal granted,
            BigDecimal reserved,
            BigDecimal consumed,
            BigDecimal remaining
    ) {
    }

    public record BillingOverviewResponse(
            boolean hasActiveSubscription,
            PlanResponse plan,
            SubscriptionResponse subscription,
            QuotaResponse quota
    ) {
    }

    public record LedgerEntryResponse(
            String id,
            String entryType,
            String referenceType,
            String referenceId,
            BigDecimal grantedDelta,
            BigDecimal reservedDelta,
            BigDecimal consumedDelta,
            String description,
            Instant createdAt
    ) {
    }

    public record UsageResponse(
            String usageId,
            String requestId,
            String modelCode,
            String pricingVersion,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            BigDecimal billedQuota,
            String status,
            Instant occurredAt
    ) {
    }

    public record BillingHistoryResponse(
            List<LedgerEntryResponse> ledger,
            List<UsageResponse> usage
    ) {
        public BillingHistoryResponse {
            ledger = List.copyOf(ledger);
            usage = List.copyOf(usage);
        }
    }

    public record CreatePurchaseOrderRequest(
            @NotNull @Min(1) Long planVersionId
    ) {
    }

    public record PurchaseOrderResponse(
            String orderNo,
            Long userId,
            Long planVersionId,
            String planCode,
            String planName,
            long amountMinor,
            String currency,
            String status,
            Instant expiresAt,
            Instant paidAt,
            Instant fulfilledAt,
            String subscriptionId,
            boolean mockPaymentEnabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PaymentCapabilitiesResponse(List<String> availableMethods) {
        public PaymentCapabilitiesResponse {
            availableMethods = List.copyOf(availableMethods);
        }
    }

    public record CurrencyRevenueResponse(
            String currency,
            long amountMinor,
            long orderCount
    ) {
    }

    public record AdminBillingStatisticsResponse(
            long publishedPlans,
            long activeSubscriptions,
            long pendingOrders,
            long fulfilledOrdersThisMonth,
            List<CurrencyRevenueResponse> revenueThisMonth,
            long modelRequestsToday,
            long completedModelRequestsToday,
            long pendingReconciliationToday,
            BigDecimal billedQuotaToday,
            String reportingZone,
            Instant generatedAt
    ) {
        public AdminBillingStatisticsResponse {
            revenueThisMonth = List.copyOf(revenueThisMonth);
        }
    }
}
