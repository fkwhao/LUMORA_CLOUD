package com.lumora.cloud.user.domain.vo.admin;

import java.time.Instant;

public record UserSessionResponse(
        String id,
        String clientType,
        String deviceId,
        String deviceName,
        String ipAddress,
        String userAgent,
        String status,
        Instant expiresAt,
        Instant lastSeenAt,
        Instant revokedAt,
        Instant createdAt
) {
}
