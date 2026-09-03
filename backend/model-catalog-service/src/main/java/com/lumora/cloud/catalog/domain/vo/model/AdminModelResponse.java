package com.lumora.cloud.catalog.domain.vo.model;

import java.time.Instant;

public record AdminModelResponse(
        Long modelId,
        String code,
        String status,
        long revision,
        ModelVersionResponse draft,
        ModelVersionResponse published,
        Instant createdAt,
        Instant updatedAt
) {
}
