package com.lumora.cloud.api;

import java.util.Set;

public record UserContext(
        String userId,
        String sessionId,
        String deviceId,
        Set<String> roles,
        String clientType,
        String requestId
) {
    public UserContext {
        roles = Set.copyOf(roles);
    }
}
