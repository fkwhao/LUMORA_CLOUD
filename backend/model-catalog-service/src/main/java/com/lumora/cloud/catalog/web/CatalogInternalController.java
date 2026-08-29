package com.lumora.cloud.catalog.web;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedProviderCredential;
import com.lumora.cloud.catalog.security.InternalRequestAuthorizer;
import com.lumora.cloud.catalog.service.ProviderCredentialService;
import com.lumora.cloud.catalog.service.PublishedCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/internal/catalog")
public class CatalogInternalController {

    private final InternalRequestAuthorizer authorizer;
    private final PublishedCatalogService catalogService;
    private final ProviderCredentialService credentialService;

    public CatalogInternalController(
            InternalRequestAuthorizer authorizer,
            PublishedCatalogService catalogService,
            ProviderCredentialService credentialService
    ) {
        this.authorizer = authorizer;
        this.catalogService = catalogService;
        this.credentialService = credentialService;
    }

    @GetMapping("/models")
    public List<ResolvedModelConfig> models(HttpServletRequest request) {
        authorizer.requireModelGateway(request);
        return catalogService.resolvedModels();
    }

    @GetMapping("/models/{modelCode}")
    public ResolvedModelConfig resolve(HttpServletRequest request, @PathVariable String modelCode) {
        authorizer.requireModelGateway(request);
        return catalogService.resolve(modelCode);
    }

    @GetMapping("/credentials/{credentialReference}")
    public ResponseEntity<ResolvedProviderCredential> resolveCredential(
            HttpServletRequest request,
            @PathVariable String credentialReference
    ) {
        authorizer.requireModelGateway(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(credentialService.resolve(credentialReference));
    }
}
