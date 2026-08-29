package com.lumora.cloud.modelgateway.recovery;

import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;

import java.time.Instant;

public record RecoveryCommand(
        RecoveryOperation operation,
        String requestId,
        SettleRequest settlement,
        String reason,
        int attempts,
        Instant createdAt
) {
    public RecoveryCommand nextAttempt() {
        return new RecoveryCommand(operation, requestId, settlement, reason, attempts + 1, createdAt);
    }
}
