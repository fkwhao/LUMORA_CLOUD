package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.billing.domain.BillingTypes.SubscriptionSource;
import com.lumora.cloud.billing.domain.BillingTypes.SubscriptionStatus;

import java.time.Instant;

@TableName("billing_subscription")
public class SubscriptionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long accountId;
    private Long userId;
    private Long planVersionId;
    private String status;
    private String source;
    private String sourceReference;
    private Instant startsAt;
    private Instant endsAt;
    private Instant canceledAt;
    private Instant createdAt;
    private Instant updatedAt;

    public SubscriptionEntity() {
    }

    public static SubscriptionEntity grant(
            String id,
            Long accountId,
            Long userId,
            Long planVersionId,
            String sourceReference,
            Instant startsAt,
            Instant endsAt
    ) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.id = id;
        entity.accountId = accountId;
        entity.userId = userId;
        entity.planVersionId = planVersionId;
        entity.status = SubscriptionStatus.ACTIVE.name();
        entity.source = SubscriptionSource.ADMIN_GRANT.name();
        entity.sourceReference = sourceReference;
        entity.startsAt = startsAt;
        entity.endsAt = endsAt;
        return entity;
    }

    public static SubscriptionEntity purchase(
            String id,
            Long accountId,
            Long userId,
            Long planVersionId,
            String orderNo,
            Instant startsAt,
            Instant endsAt
    ) {
        SubscriptionEntity entity = grant(
                id, accountId, userId, planVersionId, orderNo, startsAt, endsAt
        );
        entity.source = SubscriptionSource.PURCHASE.name();
        return entity;
    }

    public String getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public Long getPlanVersionId() { return planVersionId; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getSourceReference() { return sourceReference; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getCanceledAt() { return canceledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
