package com.lumora.cloud.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("lumora")
public record GatewayAuthProperties(
        @Valid @NotNull Auth auth,
        @Valid @NotNull Security security
) {
    public record Auth(@Valid @NotNull Jwt jwt) {
    }

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotBlank String audience
    ) {
        public Jwt {
            if (secret != null && secret.length() < 32) {
                throw new IllegalArgumentException("lumora.auth.jwt.secret must contain at least 32 characters");
            }
        }
    }

    public record Security(@NotBlank String internalToken) {
    }
}
