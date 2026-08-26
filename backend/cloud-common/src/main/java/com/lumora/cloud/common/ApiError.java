package com.lumora.cloud.common;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String traceId,
        Instant occurredAt
) {
}
