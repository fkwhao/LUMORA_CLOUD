package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("billing_plan_version_model")
public class PlanVersionModelEntity {

    private Long planVersionId;
    private String modelCode;
    private Integer displayOrder;
    private Instant createdAt;

    public PlanVersionModelEntity() {
    }

    public static PlanVersionModelEntity create(Long planVersionId, String modelCode, int displayOrder) {
        PlanVersionModelEntity entity = new PlanVersionModelEntity();
        entity.planVersionId = planVersionId;
        entity.modelCode = modelCode;
        entity.displayOrder = displayOrder;
        return entity;
    }

    public Long getPlanVersionId() { return planVersionId; }
    public String getModelCode() { return modelCode; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
