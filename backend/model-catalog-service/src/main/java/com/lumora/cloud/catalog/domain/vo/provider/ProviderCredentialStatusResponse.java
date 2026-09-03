package com.lumora.cloud.catalog.domain.vo.provider;

import java.time.Instant;

public record ProviderCredentialStatusResponse(
        String storageType,
        boolean managed,
        String maskedValue,
        String fingerprint,
        long revision,
        Instant rotatedAt
) {
}
