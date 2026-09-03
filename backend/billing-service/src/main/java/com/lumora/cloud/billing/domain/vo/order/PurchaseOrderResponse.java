package com.lumora.cloud.billing.domain.vo.order;

import java.time.Instant;

public record PurchaseOrderResponse(
        String orderNo,
        Long userId,
        Long planVersionId,
        String planCode,
        String planName,
        long amountMinor,
        String currency,
        String status,
        String paymentProvider,
        Instant expiresAt,
        Instant paidAt,
        Instant fulfilledAt,
        String subscriptionId,
        boolean mockPaymentEnabled,
        Instant createdAt,
        Instant updatedAt
) {
}
