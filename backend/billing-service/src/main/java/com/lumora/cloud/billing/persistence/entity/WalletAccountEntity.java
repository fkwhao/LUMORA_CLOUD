package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("wallet_account")
public class WalletAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String currency;
    private Long availableMinor;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCurrency() { return currency; }
    public Long getAvailableMinor() { return availableMinor; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
