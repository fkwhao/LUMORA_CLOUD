package com.lumora.cloud.billing.domain.dto.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReconciliationBatchRequest(
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 64) String> requestIds,
        @NotNull ReconciliationRequest.Action action,
        @NotBlank @Size(max = 160) String reason
) {}
