package com.lumora.cloud.catalog.domain.dto.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateModelStatusRequest(
        @Min(0) long expectedRevision,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status
) {
}
