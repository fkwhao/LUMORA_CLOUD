package com.lumora.cloud.api.catalog;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.api.fallback.CatalogClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "lumora-model-catalog-service",
        path = "/internal/catalog",
        fallbackFactory = CatalogClientFallbackFactory.class
)
public interface CatalogClient {

    @GetMapping("/models")
    List<ResolvedModelConfig> publishedModels();

    @GetMapping("/model-references")
    List<PublishedModelReference> publishedModelReferences();

    @GetMapping("/models/{modelCode}")
    ResolvedModelConfig resolve(@PathVariable("modelCode") String modelCode);

    @GetMapping("/credentials/{credentialReference}")
    ResolvedProviderCredential resolveCredential(
            @PathVariable("credentialReference") String credentialReference
    );
}
