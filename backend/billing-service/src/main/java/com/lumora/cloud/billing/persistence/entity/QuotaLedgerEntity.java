package com.lumora.cloud.billing.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("quota_ledger")
public class QuotaLedgerEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String quotaBucketId;
    private String reservationId;
    private String entryType;
    private String referenceType;
    private String referenceId;
    private BigDecimal grantedDelta;
    private BigDecimal reservedDelta;
    private BigDecimal consumedDelta;
    private String description;
    private Instant createdAt;

    public QuotaLedgerEntity() {
    }

    public static QuotaLedgerEntity create(
            String id,
            Long userId,
            String quotaBucketId,
            String reservationId,
            String entryType,
            String referenceType,
            String referenceId,
            BigDecimal grantedDelta,
            BigDecimal reservedDelta,
            BigDecimal consumedDelta,
            String description
    ) {
        QuotaLedgerEntity entity = new QuotaLedgerEntity();
        entity.id = id;
        entity.userId = userId;
        entity.quotaBucketId = quotaBucketId;
        entity.reservationId = reservationId;
        entity.entryType = entryType;
        entity.referenceType = referenceType;
        entity.referenceId = referenceId;
        entity.grantedDelta = grantedDelta;
        entity.reservedDelta = reservedDelta;
        entity.consumedDelta = consumedDelta;
        entity.description = description;
        return entity;
    }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getQuotaBucketId() { return quotaBucketId; }
    public String getReservationId() { return reservationId; }
    public String getEntryType() { return entryType; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public BigDecimal getGrantedDelta() { return grantedDelta; }
    public BigDecimal getReservedDelta() { return reservedDelta; }
    public BigDecimal getConsumedDelta() { return consumedDelta; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
