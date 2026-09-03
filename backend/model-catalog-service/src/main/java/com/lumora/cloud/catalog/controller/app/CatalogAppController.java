package com.lumora.cloud.catalog.controller.app;

import com.lumora.cloud.catalog.security.CatalogAccess;
import com.lumora.cloud.catalog.service.IPublishedCatalogService;
import com.lumora.cloud.catalog.domain.vo.model.PublicModelResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/catalog")
public class CatalogAppController {

    private final CatalogAccess access;
    private final IPublishedCatalogService catalogService;

    public CatalogAppController(CatalogAccess access, IPublishedCatalogService catalogService) {
        this.access = access;
        this.catalogService = catalogService;
    }

    @GetMapping("/models")
    public List<PublicModelResponse> models() {
        access.requireUser();
        return catalogService.publicModels();
    }
}
