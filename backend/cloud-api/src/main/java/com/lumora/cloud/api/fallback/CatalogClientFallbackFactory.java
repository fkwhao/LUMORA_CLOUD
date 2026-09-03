package com.lumora.cloud.api.fallback;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    private static final String SERVICE_NAME = "lumora-model-catalog-service";

    @Override
    public CatalogClient create(Throwable cause) {
        return new CatalogClient() {
            @Override
            public List<ResolvedModelConfig> publishedModels() {
                throw failure("publishedModels", cause);
            }

            @Override
            public List<PublishedModelReference> publishedModelReferences() {
                throw failure("publishedModelReferences", cause);
            }

            @Override
            public ResolvedModelConfig resolve(String modelCode) {
                throw failure("resolve", cause);
            }

            @Override
            public ResolvedProviderCredential resolveCredential(String credentialReference) {
                throw failure("resolveCredential", cause);
            }
        };
    }

    private RuntimeException failure(String operation, Throwable cause) {
        return RemoteServiceFallbacks.failure(SERVICE_NAME, operation, cause);
    }
}
