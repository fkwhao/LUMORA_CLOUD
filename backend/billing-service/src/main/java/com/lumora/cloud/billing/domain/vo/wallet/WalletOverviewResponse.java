package com.lumora.cloud.billing.domain.vo.wallet;

import java.util.List;

public record WalletOverviewResponse(
        Long userId,
        List<WalletAccountResponse> accounts,
        List<WalletTopupOrderResponse> topupOrders,
        List<WalletLedgerEntryResponse> ledger
) {
    public WalletOverviewResponse {
        accounts = List.copyOf(accounts);
        topupOrders = List.copyOf(topupOrders);
        ledger = List.copyOf(ledger);
    }
}
