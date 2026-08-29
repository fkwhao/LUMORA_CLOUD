package com.lumora.cloud.modelgateway.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import feign.FeignException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EnvironmentProviderCredentialResolver implements ProviderCredentialResolver {

    private static final String MANAGED_REFERENCE_PREFIX = "cred_";

    private final Environment environment;
    private final CatalogClient catalogClient;
    private final Cache<String, String> managedCredentials;

    public EnvironmentProviderCredentialResolver(
            Environment environment,
            CatalogClient catalogClient,
            ModelGatewayProperties properties
    ) {
        this.environment = environment;
        this.catalogClient = catalogClient;
        this.managedCredentials = Caffeine.newBuilder()
                .maximumSize(properties.catalog().maximumSize())
                .expireAfterWrite(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw unavailable(null);
        }
        if (reference != null && reference.startsWith(MANAGED_REFERENCE_PREFIX)) {
            try {
                String secret = managedCredentials.get(reference,
                        key -> catalogClient.resolveCredential(key).secret());
                if (secret == null || secret.isBlank()) {
                    throw unavailable(null);
                }
                return secret;
            } catch (FeignException exception) {
                throw unavailable(exception);
            } catch (ApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw unavailable(exception);
            }
        }
        String value = System.getenv(reference);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(reference);
        }
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_CREDENTIAL_UNAVAILABLE",
                    "模型供应商凭据尚未配置");
        }
        return value.trim();
    }

    @Override
    public void invalidate(String reference) {
        if (reference != null && reference.startsWith(MANAGED_REFERENCE_PREFIX)) {
            managedCredentials.invalidate(reference);
        }
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_CREDENTIAL_UNAVAILABLE",
                "模型供应商凭据尚未配置或暂时无法读取", cause);
    }
}
