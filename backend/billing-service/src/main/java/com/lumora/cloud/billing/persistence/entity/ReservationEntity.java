package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("billing_reservation")
public class ReservationEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String requestId;
    private String clientRequestId;
    private Long userId;
    private String modelCode;
    private String pricingVersion;
    private String quotaBucketId;
    private BigDecimal requestedQuota;
    private BigDecimal settledQuota;
    private String status;
    private String failureReason;
    private Instant expiresAt;
    private Instant settledAt;
    private Instant releasedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public ReservationEntity() {
    }

    public static ReservationEntity processing(
            String id,
            String requestId,
            String clientRequestId,
            Long userId,
            String modelCode,
            String pricingVersion,
            BigDecimal requestedQuota,
            Instant expiresAt
    ) {
        ReservationEntity entity = new ReservationEntity();
        entity.id = id;
        entity.requestId = requestId;
        entity.clientRequestId = clientRequestId;
        entity.userId = userId;
        entity.modelCode = modelCode;
        entity.pricingVersion = pricingVersion;
        entity.requestedQuota = requestedQuota;
        entity.status = ReservationStatus.PROCESSING.name();
        entity.expiresAt = expiresAt;
        return entity;
    }

    public String getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getClientRequestId() { return clientRequestId; }
    public Long getUserId() { return userId; }
    public String getModelCode() { return modelCode; }
    public String getPricingVersion() { return pricingVersion; }
    public String getQuotaBucketId() { return quotaBucketId; }
    public BigDecimal getRequestedQuota() { return requestedQuota; }
    public BigDecimal getSettledQuota() { return settledQuota; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
