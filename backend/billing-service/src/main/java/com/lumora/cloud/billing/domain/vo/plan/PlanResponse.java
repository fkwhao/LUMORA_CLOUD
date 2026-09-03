package com.lumora.cloud.billing.domain.vo.plan;

import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
        Long planId,
        String code,
        String name,
        String description,
        Long planVersionId,
        int versionNo,
        long monthlyPriceMinor,
        String currency,
        BigDecimal weeklyQuota,
        String modelAccessMode,
        List<String> modelCodes
) {
    public PlanResponse {
        modelCodes = List.copyOf(modelCodes);
    }
}
