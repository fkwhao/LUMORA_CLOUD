package com.lumora.cloud.catalog.domain.dto.provider;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProviderRequest(
        @Min(0) long expectedRevision,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
        @NotBlank @Size(max = 500) String baseUrl,
        @Min(1) @Max(1_000_000) Integer maxConcurrency,
        @Min(1) @Max(10_000_000) Integer requestsPerMinute,
        @Min(1) Long tokensPerMinute,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status
) {
    public UpdateProviderRequest(
            long expectedRevision, String name, String protocolType, String baseUrl, String status
    ) {
        this(expectedRevision, name, protocolType, baseUrl, null, null, null, status);
    }
}
