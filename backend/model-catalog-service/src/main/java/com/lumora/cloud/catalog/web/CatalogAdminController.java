package com.lumora.cloud.catalog.web;

import com.lumora.cloud.catalog.security.CatalogAccess;
import com.lumora.cloud.catalog.service.ModelAdministrationService;
import com.lumora.cloud.catalog.service.ProviderService;
import com.lumora.cloud.catalog.service.CatalogStatisticsService;
import com.lumora.cloud.catalog.web.CatalogWebContracts.AdminCatalogStatisticsResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.AdminModelResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateModelRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateModelRouteRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.CreateProviderRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelVersionResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ModelRouteResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.ProviderResponse;
import com.lumora.cloud.catalog.web.CatalogWebContracts.RotateProviderCredentialRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.PublishDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateDraftRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelStatusRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateModelRouteRequest;
import com.lumora.cloud.catalog.web.CatalogWebContracts.UpdateProviderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {

    private final CatalogAccess access;
    private final ProviderService providerService;
    private final ModelAdministrationService modelService;
    private final CatalogStatisticsService statisticsService;

    public CatalogAdminController(
            CatalogAccess access,
            ProviderService providerService,
            ModelAdministrationService modelService,
            CatalogStatisticsService statisticsService
    ) {
        this.access = access;
        this.providerService = providerService;
        this.modelService = modelService;
        this.statisticsService = statisticsService;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers() {
        access.requireAdmin();
        return providerService.list();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse createProvider(@Valid @RequestBody CreateProviderRequest request) {
        access.requireAdmin();
        return providerService.create(request);
    }

    @PutMapping("/providers/{providerId}")
    public ProviderResponse updateProvider(
            @PathVariable Long providerId,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        access.requireAdmin();
        return providerService.update(providerId, request);
    }

    @PutMapping("/providers/{providerId}/credential")
    public ProviderResponse rotateProviderCredential(
            @PathVariable Long providerId,
            @Valid @RequestBody RotateProviderCredentialRequest request
    ) {
        access.requireAdmin();
        return providerService.rotateCredential(providerId, request);
    }

    @GetMapping("/models")
    public List<AdminModelResponse> models() {
        access.requireAdmin();
        return modelService.list();
    }

    @GetMapping("/statistics")
    public AdminCatalogStatisticsResponse statistics() {
        access.requireAdmin();
        return statisticsService.statistics();
    }

    @GetMapping("/models/{modelId}/versions")
    public List<ModelVersionResponse> modelVersions(@PathVariable Long modelId) {
        access.requireAdmin();
        return modelService.history(modelId);
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminModelResponse createModel(@Valid @RequestBody CreateModelRequest request) {
        access.requireAdmin();
        return modelService.create(request);
    }

    @PostMapping("/models/{modelId}/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminModelResponse createDraft(@PathVariable Long modelId) {
        access.requireAdmin();
        return modelService.createDraft(modelId);
    }

    @PutMapping("/models/{modelId}/draft")
    public AdminModelResponse updateDraft(
            @PathVariable Long modelId,
            @Valid @RequestBody UpdateDraftRequest request
    ) {
        access.requireAdmin();
        return modelService.updateDraft(modelId, request);
    }

    @PostMapping("/models/{modelId}/draft/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelRouteResponse createRoute(
            @PathVariable Long modelId,
            @Valid @RequestBody CreateModelRouteRequest request
    ) {
        access.requireAdmin();
        return modelService.createRoute(modelId, request);
    }

    @PutMapping("/models/{modelId}/draft/routes/{routeId}")
    public ModelRouteResponse updateRoute(
            @PathVariable Long modelId,
            @PathVariable String routeId,
            @Valid @RequestBody UpdateModelRouteRequest request
    ) {
        access.requireAdmin();
        return modelService.updateRoute(modelId, routeId, request);
    }

    @DeleteMapping("/models/{modelId}/draft/routes/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoute(
            @PathVariable Long modelId,
            @PathVariable String routeId,
            @RequestParam long expectedRevision
    ) {
        access.requireAdmin();
        modelService.deleteRoute(modelId, routeId, expectedRevision);
    }

    @DeleteMapping("/models/{modelId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(
            @PathVariable Long modelId,
            @RequestParam long expectedRevision
    ) {
        access.requireAdmin();
        modelService.discardDraft(modelId, expectedRevision);
    }

    @PostMapping("/models/{modelId}/draft/publish")
    public AdminModelResponse publishDraft(
            @PathVariable Long modelId,
            @Valid @RequestBody PublishDraftRequest request
    ) {
        access.requireAdmin();
        return modelService.publishDraft(modelId, request);
    }

    @PutMapping("/models/{modelId}/status")
    public AdminModelResponse updateStatus(
            @PathVariable Long modelId,
            @Valid @RequestBody UpdateModelStatusRequest request
    ) {
        access.requireAdmin();
        return modelService.updateStatus(modelId, request);
    }
}
