package com.lumora.cloud.modelgateway.provider;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentProviderCredentialResolverTest {

    @Test
    void resolvesManagedCredentialThroughCatalogAndCachesIt() {
        CatalogClient catalog = mock(CatalogClient.class);
        when(catalog.resolveCredential("cred_test")).thenReturn(
                new ResolvedProviderCredential("cred_test", "managed-secret", "ABC", Instant.now())
        );
        var resolver = new EnvironmentProviderCredentialResolver(
                new MockEnvironment(), catalog, properties()
        );

        assertThat(resolver.resolve("cred_test")).isEqualTo("managed-secret");
        assertThat(resolver.resolve("cred_test")).isEqualTo("managed-secret");
        verify(catalog, times(1)).resolveCredential("cred_test");
    }

    @Test
    void keepsEnvironmentPropertyFallbackForLegacyReferences() {
        var environment = new MockEnvironment().withProperty("LEGACY_PROVIDER_KEY", "legacy-secret");
        var resolver = new EnvironmentProviderCredentialResolver(
                environment, mock(CatalogClient.class), properties()
        );

        assertThat(resolver.resolve("LEGACY_PROVIDER_KEY")).isEqualTo("legacy-secret");
    }

    private ModelGatewayProperties properties() {
        return new ModelGatewayProperties(
                new ModelGatewayProperties.Catalog(Duration.ofSeconds(30), 100),
                new ModelGatewayProperties.Concurrency(3, 100, Duration.ofMinutes(5), Duration.ofMinutes(10)),
                new ModelGatewayProperties.Provider(
                        Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofMinutes(3),
                        100, 100, Duration.ofSeconds(3), Duration.ofMinutes(2), 1_048_576, 16_777_216
                ),
                new ModelGatewayProperties.Recovery(Duration.ofDays(7), Duration.ofSeconds(10), 100)
        );
    }
}
