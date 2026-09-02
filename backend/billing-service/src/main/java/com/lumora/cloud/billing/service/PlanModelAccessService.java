package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.persistence.entity.PlanVersionEntity;
import com.lumora.cloud.billing.persistence.mapper.PlanVersionMapper;
import com.lumora.cloud.billing.persistence.mapper.PlanVersionModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlanModelAccessService {

    private final PlanVersionMapper versionMapper;
    private final PlanVersionModelMapper modelMapper;

    public PlanModelAccessService(PlanVersionMapper versionMapper, PlanVersionModelMapper modelMapper) {
        this.versionMapper = versionMapper;
        this.modelMapper = modelMapper;
    }

    public void requireAllowed(Long planVersionId, String modelCode) {
        PlanVersionEntity version = versionMapper.findPublishedById(planVersionId);
        if (version == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBSCRIPTION_PLAN_VERSION_INVALID",
                    "当前订阅关联的套餐版本不可用");
        }
        if ("ALL_PUBLISHED_LEGACY".equals(version.getModelAccessMode())) {
            return;
        }
        if (!modelMapper.containsModel(planVersionId, modelCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MODEL_NOT_INCLUDED_IN_PLAN",
                    "当前套餐不包含所选模型");
        }
    }
}
