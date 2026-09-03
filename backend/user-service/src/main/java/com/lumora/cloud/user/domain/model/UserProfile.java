package com.lumora.cloud.user.domain.model;

import java.util.Set;

public record UserProfile(String id, String email, String displayName, String status, Set<String> roles) {
    public UserProfile {
        roles = Set.copyOf(roles);
    }
}
