package com.lumora.cloud.billing.domain.dto.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreatePlanVersionRequest(
        @Min(0) long monthlyPriceMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal weeklyQuota,
        @NotEmpty @Size(max = 200) List<@Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,127}") String> modelCodes
) {
}
