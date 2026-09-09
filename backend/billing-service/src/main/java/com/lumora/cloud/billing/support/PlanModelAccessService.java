package com.lumora.cloud.billing.support;

import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.cache.PlanConfigurationCache;
import com.lumora.cloud.billing.cache.PlanConfigurationCache.ModelAccess;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanModelAccessService {

    private final PlanVersionMapper versionMapper;
    private final PlanVersionModelMapper modelMapper;
    private final PlanConfigurationCache cache;

    public void requireAllowed(Long planVersionId, String modelCode) {
        ModelAccess access = cache.modelAccess(planVersionId, () -> loadAccess(planVersionId));
        if (!"ALL_PUBLISHED_LEGACY".equals(access.mode()) && !access.modelCodes().contains(modelCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MODEL_NOT_INCLUDED_IN_PLAN",
                    "当前套餐不包含所选模型");
        }
    }

    private ModelAccess loadAccess(Long planVersionId) {
        PlanVersionEntity version = versionMapper.findPublishedById(planVersionId);
        if (version == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBSCRIPTION_PLAN_VERSION_INVALID",
                    "当前订阅关联的套餐版本不可用");
        }
        if ("ALL_PUBLISHED_LEGACY".equals(version.getModelAccessMode())) {
            return new ModelAccess(version.getModelAccessMode(), java.util.List.of());
        }
        return new ModelAccess(version.getModelAccessMode(), modelMapper.findModelCodes(planVersionId));
    }
}
