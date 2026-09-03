package com.lumora.cloud.user.domain.vo.admin;

import java.time.Instant;

public record AdminUserStatisticsResponse(
        long totalUsers,
        long activeUsers,
        long disabledUsers,
        long createdThisMonth,
        long activeSessions,
        String reportingZone,
        Instant generatedAt
) {
}
