package com.lumora.cloud.user.domain.vo.auth;

import java.util.Set;

public record UserProfileResponse(
        String id,
        String email,
        String displayName,
        String status,
        Set<String> roles
) {
    public UserProfileResponse {
        roles = Set.copyOf(roles);
    }
}
