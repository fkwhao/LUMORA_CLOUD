package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("billing_account")
public class BillingAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Instant createdAt;
    private Instant updatedAt;

    public BillingAccountEntity() {
    }

    public static BillingAccountEntity create(Long userId) {
        BillingAccountEntity entity = new BillingAccountEntity();
        entity.userId = userId;
        return entity;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
