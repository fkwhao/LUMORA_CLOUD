package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.UsageStatus;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("billing_usage_record")
public class UsageRecordEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String usageId;
    private String reservationId;
    private String requestId;
    private Long userId;
    private String modelCode;
    private String pricingVersion;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheReadTokens;
    private Long cacheWriteTokens;
    private BigDecimal billedQuota;
    private String status;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UsageRecordEntity() {
    }

    public static UsageRecordEntity processing(
            String id,
            ReservationEntity reservation,
            SettleRequest request,
            Instant occurredAt
    ) {
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.id = id;
        entity.usageId = request.usageId();
        entity.reservationId = reservation.getId();
        entity.requestId = reservation.getRequestId();
        entity.userId = reservation.getUserId();
        entity.modelCode = reservation.getModelCode();
        entity.pricingVersion = request.pricingVersion();
        entity.inputTokens = request.inputTokens();
        entity.outputTokens = request.outputTokens();
        entity.reasoningTokens = request.reasoningTokens();
        entity.cacheReadTokens = request.cacheReadTokens();
        entity.cacheWriteTokens = request.cacheWriteTokens();
        entity.billedQuota = request.billedQuota();
        entity.status = UsageStatus.PROCESSING.name();
        entity.occurredAt = occurredAt;
        return entity;
    }

    public String getId() { return id; }
    public String getUsageId() { return usageId; }
    public String getReservationId() { return reservationId; }
    public String getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public String getModelCode() { return modelCode; }
    public String getPricingVersion() { return pricingVersion; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public Long getReasoningTokens() { return reasoningTokens; }
    public Long getCacheReadTokens() { return cacheReadTokens; }
    public Long getCacheWriteTokens() { return cacheWriteTokens; }
    public BigDecimal getBilledQuota() { return billedQuota; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
