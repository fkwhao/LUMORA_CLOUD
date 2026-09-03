package com.lumora.cloud.billing.service.impl;

import com.lumora.cloud.billing.service.IBillingCatalogService;
import com.lumora.cloud.billing.service.ISubscriptionService;
import com.lumora.cloud.billing.service.IWalletService;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.domain.entity.order.PurchaseOrderEntity;
import com.lumora.cloud.billing.mapper.order.PaymentAttemptMapper;
import com.lumora.cloud.billing.mapper.order.PurchaseOrderMapper;
import com.lumora.cloud.billing.domain.dto.order.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceImplTest {

    private final PurchaseOrderMapper orderMapper = mock(PurchaseOrderMapper.class);
    private final PaymentAttemptMapper paymentMapper = mock(PaymentAttemptMapper.class);
    private final IBillingCatalogService catalogService = mock(IBillingCatalogService.class);
    private final ISubscriptionService subscriptionService = mock(ISubscriptionService.class);
    private final IWalletService walletService = mock(IWalletService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void createsImmutablePendingOrderAndExposesMockCapability() {
        PaymentProperties properties = new PaymentProperties(true, Duration.ofMinutes(30), Duration.ofDays(30));
        PurchaseOrderServiceImpl service = service(properties);
        PlanResponse plan = new PlanResponse(
                10L, "pro", "Lumora Pro", "test", 20L, 3,
                9_900L, "CNY", new BigDecimal("100.000000"), "SELECTED", java.util.List.of("test-model")
        );
        when(catalogService.publishedVersion(20L)).thenReturn(plan);

        AtomicReference<PurchaseOrderEntity> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        }).when(orderMapper).insertPendingIgnore(any(PurchaseOrderEntity.class));
        when(orderMapper.findByIdempotencyForUpdate(7L, "web-retry-1"))
                .thenAnswer(invocation -> inserted.get());

        var order = service.create(7L, " web-retry-1 ", new CreatePurchaseOrderRequest(20L));

        assertThat(order.orderNo()).matches("LU[A-Z0-9]{20,38}");
        assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.planName()).isEqualTo("Lumora Pro");
        assertThat(order.amountMinor()).isEqualTo(9_900L);
        assertThat(order.currency()).isEqualTo("CNY");
        assertThat(order.mockPaymentEnabled()).isTrue();
        assertThat(service.capabilities().availableMethods()).containsExactly("WALLET", "MOCK");
        verify(events).publishEvent(any(com.lumora.cloud.billing.messaging.order.OrderExpiryScheduledEvent.class));
    }

    @Test
    void expiresPendingOrderIdempotentlyFromMessage() {
        PurchaseOrderServiceImpl service = service(new PaymentProperties(
                true, Duration.ofMinutes(30), Duration.ofDays(30)
        ));
        Instant now = Instant.parse("2026-09-01T08:00:00Z");
        when(orderMapper.expirePendingOrder("LU20260901070000ABCDEFGHIJKL", now)).thenReturn(1);

        assertThat(service.expirePending("LU20260901070000ABCDEFGHIJKL", now)).isTrue();
        assertThat(service.expirePending("LU20260901070000MISSINGORDER", now)).isFalse();
    }

    @Test
    void rejectsMockPaymentBeforeReadingOrderWhenCapabilityIsDisabled() {
        PurchaseOrderServiceImpl service = service(new PaymentProperties(
                false, Duration.ofMinutes(30), Duration.ofDays(30)
        ));

        assertThatThrownBy(() -> service.mockPay(7L, "LU20260829120000ABCDEFGHIJKL"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("MOCK_PAYMENT_DISABLED");
        assertThat(service.capabilities().availableMethods()).containsExactly("WALLET");
        verify(orderMapper, never()).findByOrderNoForUpdate(any());
    }

    private PurchaseOrderServiceImpl service(PaymentProperties properties) {
        return new PurchaseOrderServiceImpl(
                orderMapper, paymentMapper, catalogService, subscriptionService, walletService, properties, events
        );
    }
}
