package com.lumora.cloud.catalog.service;

import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.catalog.domain.vo.model.PublicModelResponse;

import java.util.List;

public interface IPublishedCatalogService {

    List<ResolvedModelConfig> resolvedModels();

    ResolvedModelConfig resolve(String modelCode);

    List<PublicModelResponse> publicModels();

    List<PublishedModelReference> publishedModelReferences();
}
