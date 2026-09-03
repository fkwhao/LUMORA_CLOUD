package com.lumora.cloud.catalog.domain.vo.statistics;

import java.time.Instant;

public record AdminCatalogStatisticsResponse(
        long totalProviders,
        long activeProviders,
        long totalModels,
        long publicModels,
        long draftModels,
        long totalVersions,
        Instant generatedAt
) {
}
