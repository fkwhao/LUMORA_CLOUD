package com.lumora.cloud.billing.domain.entity.plan;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.billing.domain.enums.PlanStatus;

import java.time.Instant;

@TableName("billing_plan")
public class BillingPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public BillingPlanEntity() {
    }

    public static BillingPlanEntity create(String code, String name, String description) {
        BillingPlanEntity entity = new BillingPlanEntity();
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.status = PlanStatus.ACTIVE.name();
        return entity;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
