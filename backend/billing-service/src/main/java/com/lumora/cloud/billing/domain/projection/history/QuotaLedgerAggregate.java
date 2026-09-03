package com.lumora.cloud.billing.domain.projection.history;

import java.math.BigDecimal;

public class QuotaLedgerAggregate {

    private Long entryCount;
    private BigDecimal grantedDelta;
    private BigDecimal reservedDelta;
    private BigDecimal consumedDelta;

    public QuotaLedgerAggregate() {
    }

    public Long getEntryCount() { return entryCount; }
    public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
    public BigDecimal getGrantedDelta() { return grantedDelta; }
    public void setGrantedDelta(BigDecimal grantedDelta) { this.grantedDelta = grantedDelta; }
    public BigDecimal getReservedDelta() { return reservedDelta; }
    public void setReservedDelta(BigDecimal reservedDelta) { this.reservedDelta = reservedDelta; }
    public BigDecimal getConsumedDelta() { return consumedDelta; }
    public void setConsumedDelta(BigDecimal consumedDelta) { this.consumedDelta = consumedDelta; }
}
