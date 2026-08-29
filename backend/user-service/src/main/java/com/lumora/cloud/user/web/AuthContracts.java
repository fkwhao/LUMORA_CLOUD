package com.lumora.cloud.user.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lumora.cloud.user.domain.AuthTypes.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class AuthContracts {

    private AuthContracts() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 2, max = 80) String displayName,
            @NotNull ClientType clientType,
            @Size(max = 64) String deviceId,
            @Size(max = 120) String deviceName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password,
            @NotNull ClientType clientType,
            @Size(max = 64) String deviceId,
            @Size(max = 120) String deviceName
    ) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record UserProfileResponse(
            String id,
            String email,
            String displayName,
            String status,
            Set<String> roles
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant sessionExpiresAt,
            UserProfileResponse user
    ) {
    }
}
