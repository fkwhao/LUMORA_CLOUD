package com.lumora.cloud.billing.domain.vo.wallet;

import java.time.Instant;

public record WalletLedgerEntryResponse(
        String id,
        Long userId,
        String currency,
        String entryType,
        String referenceType,
        String referenceId,
        long amountDelta,
        long balanceAfter,
        String description,
        Long actorUserId,
        Instant createdAt
) {
}
