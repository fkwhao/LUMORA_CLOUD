package com.lumora.cloud.catalog.domain.dto.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RotateProviderCredentialRequest(
        @Min(0) long expectedRevision,
        @NotBlank @Size(max = 8192) String apiKey
) {
}
