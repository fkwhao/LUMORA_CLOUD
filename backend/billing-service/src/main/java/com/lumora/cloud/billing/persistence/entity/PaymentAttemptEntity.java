package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.billing.domain.BillingTypes.PaymentAttemptStatus;
import com.lumora.cloud.billing.domain.BillingTypes.PaymentProvider;

import java.time.Instant;

@TableName("billing_payment_attempt")
public class PaymentAttemptEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String orderId;
    private String provider;
    private String providerPaymentId;
    private Long amountMinor;
    private String currency;
    private String status;
    private String failureReason;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;

    public PaymentAttemptEntity() {
    }

    public static PaymentAttemptEntity mockSuccess(
            String id,
            String orderId,
            String providerPaymentId,
            long amountMinor,
            String currency,
            Instant paidAt
    ) {
        return success(id, orderId, PaymentProvider.MOCK, providerPaymentId, amountMinor, currency, paidAt);
    }

    public static PaymentAttemptEntity walletSuccess(
            String id, String orderId, String providerPaymentId,
            long amountMinor, String currency, Instant paidAt
    ) {
        return success(id, orderId, PaymentProvider.WALLET, providerPaymentId, amountMinor, currency, paidAt);
    }

    private static PaymentAttemptEntity success(
            String id, String orderId, PaymentProvider provider, String providerPaymentId,
            long amountMinor, String currency, Instant paidAt
    ) {
        PaymentAttemptEntity entity = new PaymentAttemptEntity();
        entity.id = id;
        entity.orderId = orderId;
        entity.provider = provider.name();
        entity.providerPaymentId = providerPaymentId;
        entity.amountMinor = amountMinor;
        entity.currency = currency;
        entity.status = PaymentAttemptStatus.SUCCEEDED.name();
        entity.paidAt = paidAt;
        return entity;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
