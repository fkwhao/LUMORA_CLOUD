package com.lumora.cloud.catalog.domain.dto.model;

import com.lumora.cloud.catalog.domain.dto.pricing.ModelVersionInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateModelRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,126}[a-z0-9]") String code,
        @NotNull @Min(1) Long providerId,
        @NotNull @Valid ModelVersionInput version
) {
}
