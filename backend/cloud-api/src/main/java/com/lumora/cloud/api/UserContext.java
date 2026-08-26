package com.lumora.cloud.api;

import java.util.Set;

public record UserContext(
        String userId,
        String deviceId,
        Set<String> roles
) {
    public UserContext {
        roles = Set.copyOf(roles);
    }
}
