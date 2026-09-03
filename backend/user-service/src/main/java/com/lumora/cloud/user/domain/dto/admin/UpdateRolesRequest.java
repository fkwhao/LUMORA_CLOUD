package com.lumora.cloud.user.domain.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UpdateRolesRequest(
        @NotEmpty Set<@Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String> roles
) {
    public UpdateRolesRequest {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
