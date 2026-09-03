package com.lumora.cloud.catalog.domain.dto.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateModelRouteRequest(@NotNull @Valid ModelRouteInput route) {
}
