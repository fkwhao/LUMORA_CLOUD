package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.domain.dto.model.CreateModelRequest;
import com.lumora.cloud.catalog.domain.dto.model.PublishDraftRequest;
import com.lumora.cloud.catalog.domain.dto.model.UpdateDraftRequest;
import com.lumora.cloud.catalog.domain.dto.model.UpdateModelStatusRequest;
import com.lumora.cloud.catalog.domain.dto.route.CreateModelRouteRequest;
import com.lumora.cloud.catalog.domain.dto.route.UpdateModelRouteRequest;
import com.lumora.cloud.catalog.domain.vo.model.AdminModelResponse;
import com.lumora.cloud.catalog.domain.vo.model.ModelVersionResponse;
import com.lumora.cloud.catalog.domain.vo.route.ModelRouteResponse;

import java.util.List;

public interface IModelAdministrationService {

    AdminModelResponse create(CreateModelRequest request);

    AdminModelResponse createDraft(Long modelId);

    AdminModelResponse updateDraft(Long modelId, UpdateDraftRequest request);

    void discardDraft(Long modelId, long expectedRevision);

    AdminModelResponse publishDraft(Long modelId, PublishDraftRequest request);

    AdminModelResponse updateStatus(Long modelId, UpdateModelStatusRequest request);

    List<AdminModelResponse> list();

    List<ModelVersionResponse> history(Long modelId);

    ModelRouteResponse createRoute(Long modelId, CreateModelRouteRequest request);

    ModelRouteResponse updateRoute(Long modelId, String routeId, UpdateModelRouteRequest request);

    void deleteRoute(Long modelId, String routeId, long expectedRevision);
}
