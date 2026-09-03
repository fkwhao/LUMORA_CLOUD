package com.lumora.cloud.billing.domain.entity.wallet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("wallet_topup_order")
public class WalletTopupOrderEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String orderNo;
    private Long accountId;
    private Long userId;
    private Long amountMinor;
    private String currency;
    private String status;
    private String idempotencyKey;
    private Instant expiresAt;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static WalletTopupOrderEntity pending(
            String id, String orderNo, Long accountId, Long userId, long amountMinor,
            String currency, String idempotencyKey, Instant expiresAt
    ) {
        WalletTopupOrderEntity entity = new WalletTopupOrderEntity();
        entity.id = id;
        entity.orderNo = orderNo;
        entity.accountId = accountId;
        entity.userId = userId;
        entity.amountMinor = amountMinor;
        entity.currency = currency;
        entity.status = "PENDING_PAYMENT";
        entity.idempotencyKey = idempotencyKey;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
