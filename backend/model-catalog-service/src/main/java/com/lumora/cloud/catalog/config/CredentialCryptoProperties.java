package com.lumora.cloud.catalog.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("lumora.catalog.credentials")
public record CredentialCryptoProperties(
        @NotBlank String masterKey,
        @Min(1) int keyVersion
) {
}
