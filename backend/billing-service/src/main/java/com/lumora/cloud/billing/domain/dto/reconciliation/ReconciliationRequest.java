package com.lumora.cloud.billing.domain.dto.reconciliation;

import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReconciliationRequest(
        @NotNull Action action,
        @NotBlank @Size(max = 160) String reason,
        @Valid SettleRequest settlement
) {
    public enum Action { SETTLE, RELEASE }
}
