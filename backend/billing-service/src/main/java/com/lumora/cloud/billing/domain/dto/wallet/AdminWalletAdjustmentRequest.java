package com.lumora.cloud.billing.domain.dto.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminWalletAdjustmentRequest(
        @NotNull @Min(1) Long userId,
        @NotNull Long amountDelta,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 500) String reason
) {
}
