package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("quota_bucket")
public class QuotaBucketEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String subscriptionId;
    private Long userId;
    private Integer periodNo;
    private Instant startsAt;
    private Instant endsAt;
    private BigDecimal grantedQuota;
    private BigDecimal reservedQuota;
    private BigDecimal consumedQuota;
    private Instant createdAt;
    private Instant updatedAt;

    public QuotaBucketEntity() {
    }

    public static QuotaBucketEntity create(
            String id,
            String subscriptionId,
            Long userId,
            int periodNo,
            Instant startsAt,
            Instant endsAt,
            BigDecimal grantedQuota
    ) {
        QuotaBucketEntity entity = new QuotaBucketEntity();
        entity.id = id;
        entity.subscriptionId = subscriptionId;
        entity.userId = userId;
        entity.periodNo = periodNo;
        entity.startsAt = startsAt;
        entity.endsAt = endsAt;
        entity.grantedQuota = grantedQuota;
        entity.reservedQuota = BigDecimal.ZERO;
        entity.consumedQuota = BigDecimal.ZERO;
        return entity;
    }

    public BigDecimal availableQuota() {
        return grantedQuota.subtract(reservedQuota).subtract(consumedQuota);
    }

    public String getId() { return id; }
    public String getSubscriptionId() { return subscriptionId; }
    public Long getUserId() { return userId; }
    public Integer getPeriodNo() { return periodNo; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public BigDecimal getGrantedQuota() { return grantedQuota; }
    public BigDecimal getReservedQuota() { return reservedQuota; }
    public BigDecimal getConsumedQuota() { return consumedQuota; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
