package com.lumora.cloud.catalog.domain.dto.provider;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProviderRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String protocolType,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Size(max = 8192) String apiKey,
        @Min(1) @Max(1_000_000) Integer maxConcurrency,
        @Min(1) @Max(10_000_000) Integer requestsPerMinute,
        @Min(1) Long tokensPerMinute
) {
    public CreateProviderRequest(String code, String name, String protocolType, String baseUrl, String apiKey) {
        this(code, name, protocolType, baseUrl, apiKey, null, null, null);
    }
}
