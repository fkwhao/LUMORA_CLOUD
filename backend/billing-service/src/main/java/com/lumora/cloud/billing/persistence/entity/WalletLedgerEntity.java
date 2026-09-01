package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("wallet_ledger")
public class WalletLedgerEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long accountId;
    private Long userId;
    private String currency;
    private String entryType;
    private String referenceType;
    private String referenceId;
    private Long amountDelta;
    private Long balanceAfter;
    private String description;
    private Long actorUserId;
    private Instant createdAt;

    public static WalletLedgerEntity create(
            String id, Long accountId, Long userId, String currency, String entryType,
            String referenceType, String referenceId, long amountDelta, long balanceAfter,
            String description, Long actorUserId
    ) {
        WalletLedgerEntity entity = new WalletLedgerEntity();
        entity.id = id;
        entity.accountId = accountId;
        entity.userId = userId;
        entity.currency = currency;
        entity.entryType = entryType;
        entity.referenceType = referenceType;
        entity.referenceId = referenceId;
        entity.amountDelta = amountDelta;
        entity.balanceAfter = balanceAfter;
        entity.description = description;
        entity.actorUserId = actorUserId;
        return entity;
    }

    public String getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public String getCurrency() { return currency; }
    public String getEntryType() { return entryType; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public Long getAmountDelta() { return amountDelta; }
    public Long getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
