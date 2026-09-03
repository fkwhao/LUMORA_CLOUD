package com.lumora.cloud.catalog.domain.dto.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateModelRouteRequest(
        @Min(0) long expectedRevision,
        @NotNull @Valid ModelRouteInput route
) {
}
