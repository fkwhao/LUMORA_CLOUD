package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanVersionRequest;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;

import java.util.List;

public interface IBillingCatalogService {

    PlanResponse create(CreatePlanRequest request);

    List<PlanResponse> listPublished();

    List<PlanResponse> listAllPublished();

    PlanResponse purchasableVersion(Long planVersionId);

    PlanResponse publishVersion(Long planId, CreatePlanVersionRequest request);

    List<PlanResponse> versions(Long planId);

    PlanResponse publishedVersion(Long planVersionId);
}
