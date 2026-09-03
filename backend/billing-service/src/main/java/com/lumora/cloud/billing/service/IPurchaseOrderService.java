package com.lumora.cloud.billing.service;

import com.lumora.cloud.billing.domain.dto.order.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.domain.vo.order.PaymentCapabilitiesResponse;
import com.lumora.cloud.billing.domain.vo.order.PurchaseOrderResponse;

import java.time.Instant;
import java.util.List;

public interface IPurchaseOrderService {

    PurchaseOrderResponse create(Long userId, String idempotencyKey, CreatePurchaseOrderRequest request);

    PurchaseOrderResponse mockPay(Long userId, String orderNo);

    PurchaseOrderResponse walletPay(Long userId, String orderNo);

    PurchaseOrderResponse cancel(Long userId, String orderNo);

    boolean expirePending(String orderNo, Instant now);

    PurchaseOrderResponse get(Long userId, String orderNo);

    List<PurchaseOrderResponse> list(Long userId);

    List<PurchaseOrderResponse> listRecentForAdmin();

    PaymentCapabilitiesResponse capabilities();
}
