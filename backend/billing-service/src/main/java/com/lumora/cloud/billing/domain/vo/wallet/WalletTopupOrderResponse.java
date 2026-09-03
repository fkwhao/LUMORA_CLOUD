package com.lumora.cloud.billing.domain.vo.wallet;

import java.time.Instant;

public record WalletTopupOrderResponse(
        String orderNo,
        Long userId,
        long amountMinor,
        String currency,
        String status,
        Instant expiresAt,
        Instant paidAt,
        boolean mockPaymentEnabled,
        Instant createdAt,
        Instant updatedAt
) {
}
