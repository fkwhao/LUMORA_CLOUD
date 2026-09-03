package com.lumora.cloud.billing.domain.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record GrantSubscriptionRequest(
        @NotNull @Min(1) Long userId,
        @NotNull @Min(1) Long planVersionId,
        @NotBlank @Size(max = 128) String sourceReference,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
