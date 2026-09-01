package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.billing.domain.BillingTypes.PurchaseOrderStatus;

import java.time.Instant;

@TableName("billing_purchase_order")
public class PurchaseOrderEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String orderNo;
    private Long userId;
    private Long planVersionId;
    private String planCode;
    private String planName;
    private Long amountMinor;
    private String currency;
    private String status;
    private String paymentProvider;
    private String idempotencyKey;
    private Instant expiresAt;
    private Instant paidAt;
    private Instant fulfilledAt;
    private String subscriptionId;
    private Instant createdAt;
    private Instant updatedAt;

    public PurchaseOrderEntity() {
    }

    public static PurchaseOrderEntity pending(
            String id,
            String orderNo,
            Long userId,
            Long planVersionId,
            String planCode,
            String planName,
            long amountMinor,
            String currency,
            String idempotencyKey,
            Instant expiresAt
    ) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.id = id;
        entity.orderNo = orderNo;
        entity.userId = userId;
        entity.planVersionId = planVersionId;
        entity.planCode = planCode;
        entity.planName = planName;
        entity.amountMinor = amountMinor;
        entity.currency = currency;
        entity.status = PurchaseOrderStatus.PENDING_PAYMENT.name();
        entity.idempotencyKey = idempotencyKey;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Long getUserId() { return userId; }
    public Long getPlanVersionId() { return planVersionId; }
    public String getPlanCode() { return planCode; }
    public String getPlanName() { return planName; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getPaymentProvider() { return paymentProvider; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public String getSubscriptionId() { return subscriptionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
