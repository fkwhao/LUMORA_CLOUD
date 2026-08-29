package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.billing.domain.BillingTypes.PlanVersionStatus;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("billing_plan_version")
public class PlanVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer versionNo;
    private Long monthlyPriceMinor;
    private String currency;
    private BigDecimal weeklyQuota;
    private String status;
    private Instant publishedAt;
    private Instant createdAt;

    public PlanVersionEntity() {
    }

    public static PlanVersionEntity published(
            Long planId,
            int versionNo,
            long monthlyPriceMinor,
            String currency,
            BigDecimal weeklyQuota,
            Instant now
    ) {
        PlanVersionEntity entity = new PlanVersionEntity();
        entity.planId = planId;
        entity.versionNo = versionNo;
        entity.monthlyPriceMinor = monthlyPriceMinor;
        entity.currency = currency;
        entity.weeklyQuota = weeklyQuota;
        entity.status = PlanVersionStatus.PUBLISHED.name();
        entity.publishedAt = now;
        return entity;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public Integer getVersionNo() { return versionNo; }
    public Long getMonthlyPriceMinor() { return monthlyPriceMinor; }
    public String getCurrency() { return currency; }
    public BigDecimal getWeeklyQuota() { return weeklyQuota; }
    public String getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
