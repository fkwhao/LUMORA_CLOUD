package com.lumora.cloud.billing.domain.vo.wallet;

import java.time.Instant;

public record WalletAccountResponse(
        Long accountId,
        Long userId,
        String currency,
        long availableMinor,
        long version,
        Instant updatedAt
) {
}
