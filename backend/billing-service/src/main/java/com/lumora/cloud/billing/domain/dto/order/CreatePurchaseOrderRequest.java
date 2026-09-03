package com.lumora.cloud.billing.domain.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePurchaseOrderRequest(@NotNull @Min(1) Long planVersionId) {
}
