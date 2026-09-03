package com.lumora.cloud.billing.domain.vo.statistics;

public record CurrencyRevenueResponse(String currency, long amountMinor, long orderCount) {
}
