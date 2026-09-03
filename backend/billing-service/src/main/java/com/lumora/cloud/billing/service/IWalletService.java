package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.dto.wallet.AdminWalletAdjustmentRequest;
import com.lumora.cloud.billing.domain.dto.wallet.CreateWalletTopupRequest;
import com.lumora.cloud.billing.domain.vo.wallet.WalletAdjustmentResponse;
import com.lumora.cloud.billing.domain.vo.wallet.WalletOverviewResponse;
import com.lumora.cloud.billing.domain.vo.wallet.WalletTopupOrderResponse;

import java.time.Instant;

public interface IWalletService {

    WalletOverviewResponse overview(Long userId);

    WalletTopupOrderResponse createTopup(Long userId, String idempotencyKey, CreateWalletTopupRequest request);

    WalletTopupOrderResponse mockPayTopup(Long userId, String orderNo);

    WalletTopupOrderResponse cancelTopup(Long userId, String orderNo);

    boolean expireTopup(String orderNo, Instant now);

    WalletAdjustmentResponse adjust(Long actorUserId, String idempotencyKey, AdminWalletAdjustmentRequest request);

    WalletOverviewResponse adminOverview(Long userId);

    void debitPurchase(Long userId, String currency, long amountMinor, String orderNo);
}
