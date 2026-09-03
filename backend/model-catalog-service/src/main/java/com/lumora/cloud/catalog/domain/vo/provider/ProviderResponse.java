package com.lumora.cloud.catalog.domain.vo.provider;

import java.time.Instant;

public record ProviderResponse(
        Long id,
        String code,
        String name,
        String protocolType,
        String baseUrl,
        Integer maxConcurrency,
        Integer requestsPerMinute,
        Long tokensPerMinute,
        ProviderCredentialStatusResponse credential,
        String status,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
