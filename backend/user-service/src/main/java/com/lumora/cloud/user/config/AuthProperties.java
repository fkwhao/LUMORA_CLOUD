package com.lumora.cloud.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("lumora.auth")
public record AuthProperties(
        @Valid @NotNull Jwt jwt,
        @NotNull Duration refreshTokenTtl,
        @Valid @NotNull Cookie cookie
) {
    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl
    ) {
        public Jwt {
            if (secret != null && secret.length() < 32) {
                throw new IllegalArgumentException("lumora.auth.jwt.secret must contain at least 32 characters");
            }
        }
    }

    public record Cookie(
            @NotBlank String name,
            boolean secure,
            @NotBlank String sameSite,
            @NotBlank String path
    ) {
    }
}
