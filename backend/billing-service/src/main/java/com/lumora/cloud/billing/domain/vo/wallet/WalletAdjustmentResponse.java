package com.lumora.cloud.billing.domain.vo.wallet;

public record WalletAdjustmentResponse(
        WalletAccountResponse account,
        WalletLedgerEntryResponse ledgerEntry
) {
}
