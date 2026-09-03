package com.lumora.cloud.user.domain.vo.admin;

import java.time.Instant;
import java.util.Set;

public record AdminUserResponse(
        Long id,
        String email,
        String displayName,
        String status,
        Set<String> roles,
        long activeSessions,
        Instant createdAt
) {
    public AdminUserResponse {
        roles = Set.copyOf(roles);
    }
}
