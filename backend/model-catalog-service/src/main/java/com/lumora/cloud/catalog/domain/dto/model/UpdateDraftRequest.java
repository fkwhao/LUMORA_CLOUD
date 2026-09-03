package com.lumora.cloud.catalog.domain.dto.model;

import com.lumora.cloud.catalog.domain.dto.pricing.ModelVersionInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDraftRequest(
        @Min(0) long expectedRevision,
        @NotNull @Min(1) Long providerId,
        @NotNull @Valid ModelVersionInput version
) {
}
