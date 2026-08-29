package com.lumora.cloud.billing.persistence.projection;

public class RevenueAggregate {

    private String currency;
    private Long amountMinor;
    private Long orderCount;

    public RevenueAggregate() {
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
}
