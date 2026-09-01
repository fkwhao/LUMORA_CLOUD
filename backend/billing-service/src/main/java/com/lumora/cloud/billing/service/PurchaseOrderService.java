package com.lumora.cloud.billing.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.domain.BillingTypes.PurchaseOrderStatus;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.messaging.OrderExpiryScheduledEvent;
import com.lumora.cloud.billing.persistence.entity.PaymentAttemptEntity;
import com.lumora.cloud.billing.persistence.entity.PurchaseOrderEntity;
import com.lumora.cloud.billing.persistence.mapper.PaymentAttemptMapper;
import com.lumora.cloud.billing.persistence.mapper.PurchaseOrderMapper;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.PaymentCapabilitiesResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.PlanResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.PurchaseOrderResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.SubscriptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final PurchaseOrderMapper orderMapper;
    private final PaymentAttemptMapper paymentMapper;
    private final BillingCatalogService catalogService;
    private final SubscriptionService subscriptionService;
    private final WalletService walletService;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher events;

    public PurchaseOrderService(
            PurchaseOrderMapper orderMapper,
            PaymentAttemptMapper paymentMapper,
            BillingCatalogService catalogService,
            SubscriptionService subscriptionService,
            WalletService walletService,
            PaymentProperties properties,
            ApplicationEventPublisher events
    ) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.catalogService = catalogService;
        this.subscriptionService = subscriptionService;
        this.walletService = walletService;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public PurchaseOrderResponse create(
            Long userId,
            String idempotencyKey,
        CreatePurchaseOrderRequest request
    ) {
        String normalizedKey = idempotencyKey.trim();
        PlanResponse plan = catalogService.publishedVersion(request.planVersionId());
        Instant now = Instant.now();
        PurchaseOrderEntity pending = PurchaseOrderEntity.pending(
                UUID.randomUUID().toString(), orderNumber(now), userId, plan.planVersionId(),
                plan.code(), plan.name(), plan.monthlyPriceMinor(), plan.currency(),
                normalizedKey, now.plus(properties.orderTtl())
        );
        // Let the unique key serialize concurrent retries. Selecting a missing key
        // FOR UPDATE first would create compatible gap locks and can deadlock when
        // both transactions subsequently try to insert the same idempotency key.
        orderMapper.insertPendingIgnore(pending);
        PurchaseOrderEntity persisted = orderMapper.findByIdempotencyForUpdate(userId, normalizedKey);
        if (persisted == null) {
            throw new IllegalStateException("Purchase order was not created");
        }
        ensureSamePlan(persisted, request.planVersionId());
        if (PurchaseOrderStatus.PENDING_PAYMENT.name().equals(persisted.getStatus())) {
            events.publishEvent(new OrderExpiryScheduledEvent(persisted.getOrderNo(), persisted.getExpiresAt()));
        }
        return response(persisted);
    }

    @Transactional
    public PurchaseOrderResponse mockPay(Long userId, String orderNo) {
        if (!properties.mockEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MOCK_PAYMENT_DISABLED", "本地测试支付渠道未启用");
        }
        PurchaseOrderEntity order = requireForUpdate(orderNo, userId);
        if (!PurchaseOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return response(order);
        }

        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            orderMapper.markExpired(order.getId());
            return response(orderMapper.selectById(order.getId()));
        }

        paymentMapper.insert(PaymentAttemptEntity.mockSuccess(
                UUID.randomUUID().toString(), order.getId(), "mock:" + order.getOrderNo(),
                order.getAmountMinor(), order.getCurrency(), now
        ));
        SubscriptionResponse subscription = subscriptionService.purchase(
                userId, order.getPlanVersionId(), order.getOrderNo(), now
        );
        if (orderMapper.markFulfilled(order.getId(), now, subscription.subscriptionId(), "MOCK") != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "订单状态已被其他操作修改");
        }
        return response(orderMapper.selectById(order.getId()));
    }

    @Transactional
    public PurchaseOrderResponse walletPay(Long userId, String orderNo) {
        PurchaseOrderEntity order = requireForUpdate(orderNo, userId);
        if (!PurchaseOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return response(order);
        }
        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            orderMapper.markExpired(order.getId());
            return response(orderMapper.selectById(order.getId()));
        }

        walletService.debitPurchase(userId, order.getCurrency(), order.getAmountMinor(), order.getOrderNo());
        paymentMapper.insert(PaymentAttemptEntity.walletSuccess(
                UUID.randomUUID().toString(), order.getId(), "wallet:" + order.getOrderNo(),
                order.getAmountMinor(), order.getCurrency(), now
        ));
        SubscriptionResponse subscription = subscriptionService.purchase(
                userId, order.getPlanVersionId(), order.getOrderNo(), now
        );
        if (orderMapper.markFulfilled(order.getId(), now, subscription.subscriptionId(), "WALLET") != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "订单状态已被其他操作修改");
        }
        return response(orderMapper.selectById(order.getId()));
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long userId, String orderNo) {
        PurchaseOrderEntity order = requireForUpdate(orderNo, userId);
        if (!PurchaseOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return response(order);
        }
        if (!order.getExpiresAt().isAfter(Instant.now())) {
            orderMapper.markExpired(order.getId());
        } else {
            orderMapper.markCanceled(order.getId());
        }
        return response(orderMapper.selectById(order.getId()));
    }

    @Transactional
    public boolean expirePending(String orderNo, Instant now) {
        return orderMapper.expirePendingOrder(orderNo, now) == 1;
    }

    @Transactional
    public PurchaseOrderResponse get(Long userId, String orderNo) {
        expireUserOrders(userId);
        PurchaseOrderEntity order = orderMapper.findByUserAndOrderNo(userId, orderNo);
        if (order == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_ORDER_NOT_FOUND", "订单不存在");
        }
        return response(order);
    }

    @Transactional
    public List<PurchaseOrderResponse> list(Long userId) {
        expireUserOrders(userId);
        return orderMapper.selectList(Wrappers.<PurchaseOrderEntity>lambdaQuery()
                        .eq(PurchaseOrderEntity::getUserId, userId)
                        .orderByDesc(PurchaseOrderEntity::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public List<PurchaseOrderResponse> listRecentForAdmin() {
        orderMapper.expirePending(Instant.now(), 1000);
        return orderMapper.selectList(Wrappers.<PurchaseOrderEntity>lambdaQuery()
                        .orderByDesc(PurchaseOrderEntity::getCreatedAt)
                        .last("LIMIT 100"))
                .stream()
                .map(this::response)
                .toList();
    }

    public PaymentCapabilitiesResponse capabilities() {
        return new PaymentCapabilitiesResponse(
                properties.mockEnabled() ? List.of("WALLET", "MOCK") : List.of("WALLET")
        );
    }

    private PurchaseOrderEntity requireForUpdate(String orderNo, Long userId) {
        PurchaseOrderEntity order = orderMapper.findByOrderNoForUpdate(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_ORDER_NOT_FOUND", "订单不存在");
        }
        return order;
    }

    private void expireUserOrders(Long userId) {
        orderMapper.expireUserOrders(userId, Instant.now());
    }

    private void ensureSamePlan(PurchaseOrderEntity order, Long planVersionId) {
        if (!order.getPlanVersionId().equals(planVersionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_IDEMPOTENCY_CONFLICT",
                    "相同幂等键对应了不同套餐版本");
        }
    }

    private PurchaseOrderResponse response(PurchaseOrderEntity order) {
        return new PurchaseOrderResponse(
                order.getOrderNo(), order.getUserId(), order.getPlanVersionId(),
                order.getPlanCode(), order.getPlanName(), order.getAmountMinor(), order.getCurrency(),
                order.getStatus(), order.getPaymentProvider(), order.getExpiresAt(), order.getPaidAt(), order.getFulfilledAt(),
                order.getSubscriptionId(), properties.mockEnabled(), order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    private String orderNumber(Instant now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "LU" + ORDER_TIME.format(now) + random;
    }
}
