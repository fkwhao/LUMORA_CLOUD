package com.lumora.cloud.catalog.web;

import com.lumora.cloud.catalog.security.CatalogAccess;
import com.lumora.cloud.catalog.service.PublishedCatalogService;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublicModelResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/catalog")
public class CatalogAppController {

    private final CatalogAccess access;
    private final PublishedCatalogService catalogService;

    public CatalogAppController(CatalogAccess access, PublishedCatalogService catalogService) {
        this.access = access;
        this.catalogService = catalogService;
    }

    @GetMapping("/models")
    public List<PublicModelResponse> models() {
        access.requireUser();
        return catalogService.publicModels();
    }
}
