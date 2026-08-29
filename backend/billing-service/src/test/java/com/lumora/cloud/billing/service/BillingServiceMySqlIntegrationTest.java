package com.lumora.cloud.billing.service;

import com.lumora.cloud.api.billing.BillingContracts.ReleaseRequest;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.billing.BillingContracts.UsageStatus;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.web.BillingWebContracts.BillingHistoryResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.BillingOverviewResponse;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePlanVersionRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.GrantSubscriptionRequest;
import com.lumora.cloud.billing.web.BillingWebContracts.PlanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "lumora.billing.reservation-expiry.enabled=false",
                "lumora.billing.payment.mock-enabled=true"
        }
)
@Transactional
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_MYSQL_TESTS", matches = "true")
class BillingServiceMySqlIntegrationTest {

    @Autowired
    private BillingCatalogService catalogService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private BillingHistoryService historyService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private BillingStatisticsService statisticsService;

    @Test
    void completesIdempotentQuotaLifecycleAgainstMySql() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long userId = 9_000_000_000L + Math.abs((long) suffix.hashCode());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var statisticsBefore = statisticsService.statistics();

        PlanResponse plan = catalogService.create(new CreatePlanRequest(
                "it-" + suffix, "集成测试套餐", "仅供自动化测试", 19_900L, "CNY", amount("100")
        ));
        PlanResponse secondVersion = catalogService.publishVersion(plan.planId(), new CreatePlanVersionRequest(
                29_900L, "CNY", amount("200")
        ));
        assertThat(secondVersion.versionNo()).isEqualTo(2);
        assertThat(catalogService.versions(plan.planId()))
                .extracting(version -> version.versionNo() + ":" + version.weeklyQuota())
                .containsExactly("2:200.000000", "1:100.000000");
        assertThat(catalogService.listPublished())
                .filteredOn(candidate -> candidate.planId().equals(plan.planId()))
                .singleElement()
                .extracting(PlanResponse::planVersionId)
                .isEqualTo(secondVersion.planVersionId());

        long purchaseUserId = userId + 1;
        var pendingOrder = orderService.create(
                purchaseUserId, "it-order-" + suffix, new CreatePurchaseOrderRequest(plan.planVersionId())
        );
        var repeatedOrder = orderService.create(
                purchaseUserId, "it-order-" + suffix, new CreatePurchaseOrderRequest(plan.planVersionId())
        );
        assertThat(repeatedOrder.orderNo()).isEqualTo(pendingOrder.orderNo());
        assertThat(pendingOrder.status()).isEqualTo("PENDING_PAYMENT");

        var fulfilledOrder = orderService.mockPay(purchaseUserId, pendingOrder.orderNo());
        var repeatedPayment = orderService.mockPay(purchaseUserId, pendingOrder.orderNo());
        assertThat(fulfilledOrder.status()).isEqualTo("FULFILLED");
        assertThat(fulfilledOrder.subscriptionId()).isNotBlank();
        assertThat(repeatedPayment.subscriptionId()).isEqualTo(fulfilledOrder.subscriptionId());
        assertThat(subscriptionService.overview(purchaseUserId).hasActiveSubscription()).isTrue();
        assertThat(orderService.list(purchaseUserId)).singleElement()
                .extracting(order -> order.orderNo() + ":" + order.status())
                .isEqualTo(pendingOrder.orderNo() + ":FULFILLED");

        subscriptionService.grant(new GrantSubscriptionRequest(
                userId, plan.planVersionId(), "it-grant-" + suffix,
                now.minus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS)
        ));
        assertThat(subscriptionService.listRecent(userId))
                .singleElement()
                .satisfies(subscription -> {
                    assertThat(subscription.source()).isEqualTo("ADMIN_GRANT");
                    assertThat(subscription.sourceReference()).isEqualTo("it-grant-" + suffix);
                });

        ReserveRequest firstReserve = reserveRequest(suffix + "-1", userId, amount("20"), now);
        var reserved = settlementService.reserve(firstReserve);
        var reservedAgain = settlementService.reserve(firstReserve);
        assertThat(reservedAgain.reservationId()).isEqualTo(reserved.reservationId());
        assertThat(reserved.remainingQuota()).isEqualByComparingTo("80");
        assertThat(reserved.pricingVersion()).isEqualTo("catalog-v1");

        assertThatThrownBy(() -> settlementService.settle(
                firstReserve.requestId(), settleRequest(suffix + "-wrong-price", "catalog-v2", amount("12"), now)
        )).isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("PRICING_VERSION_MISMATCH");

        SettleRequest firstSettlement = settleRequest(suffix + "-usage-1", "catalog-v1", amount("12"), now);
        var settled = settlementService.settle(firstReserve.requestId(), firstSettlement);
        var settledAgain = settlementService.settle(firstReserve.requestId(), firstSettlement);
        assertThat(settledAgain.usageId()).isEqualTo(settled.usageId());
        assertThat(settled.reservationStatus()).isEqualTo(ReservationStatus.SETTLED);
        assertThat(settled.usageStatus()).isEqualTo(UsageStatus.COMPLETED);
        assertThat(settled.releasedQuota()).isEqualByComparingTo("8");
        assertThat(settled.remainingQuota()).isEqualByComparingTo("88");

        ReserveRequest releasedRequest = reserveRequest(suffix + "-2", userId, amount("10"), now);
        settlementService.reserve(releasedRequest);
        var released = settlementService.release(releasedRequest.requestId(), new ReleaseRequest("调用前失败"));
        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(released.remainingQuota()).isEqualByComparingTo("88");

        ReserveRequest pendingRequest = reserveRequest(suffix + "-3", userId, amount("5"), now);
        settlementService.reserve(pendingRequest);
        var pending = settlementService.settle(
                pendingRequest.requestId(),
                settleRequest(suffix + "-usage-3", "catalog-v1", amount("6"), now)
        );
        assertThat(pending.reservationStatus()).isEqualTo(ReservationStatus.PENDING_RECONCILIATION);
        assertThat(pending.usageStatus()).isEqualTo(UsageStatus.PENDING_RECONCILIATION);
        assertThat(pending.remainingQuota()).isEqualByComparingTo("83");

        BillingOverviewResponse overview = subscriptionService.overview(userId);
        assertThat(overview.hasActiveSubscription()).isTrue();
        assertThat(overview.quota().granted()).isEqualByComparingTo("100");
        assertThat(overview.quota().reserved()).isEqualByComparingTo("5");
        assertThat(overview.quota().consumed()).isEqualByComparingTo("12");
        assertThat(overview.quota().remaining()).isEqualByComparingTo("83");

        BillingHistoryResponse history = historyService.recent(userId);
        assertThat(history.ledger()).hasSize(6);
        assertThat(history.usage()).hasSize(2);

        var statisticsAfter = statisticsService.statistics();
        assertThat(statisticsAfter.publishedPlans() - statisticsBefore.publishedPlans()).isEqualTo(1);
        assertThat(statisticsAfter.activeSubscriptions() - statisticsBefore.activeSubscriptions()).isEqualTo(2);
        assertThat(statisticsAfter.fulfilledOrdersThisMonth() - statisticsBefore.fulfilledOrdersThisMonth())
                .isEqualTo(1);
        assertThat(revenue(statisticsAfter, "CNY") - revenue(statisticsBefore, "CNY")).isEqualTo(19_900L);
        assertThat(statisticsAfter.modelRequestsToday() - statisticsBefore.modelRequestsToday()).isEqualTo(2);
        assertThat(statisticsAfter.completedModelRequestsToday()
                - statisticsBefore.completedModelRequestsToday()).isEqualTo(1);
        assertThat(statisticsAfter.pendingReconciliationToday()
                - statisticsBefore.pendingReconciliationToday()).isEqualTo(1);
        assertThat(statisticsAfter.billedQuotaToday().subtract(statisticsBefore.billedQuotaToday()))
                .isEqualByComparingTo("18");
    }

    private ReserveRequest reserveRequest(String id, long userId, BigDecimal maximumQuota, Instant now) {
        return new ReserveRequest(
                "request-" + id, "client-" + id, userId, "openai-gpt-test", "catalog-v1",
                maximumQuota, now.plus(10, ChronoUnit.MINUTES)
        );
    }

    private SettleRequest settleRequest(String usageId, String pricingVersion, BigDecimal billedQuota, Instant now) {
        return new SettleRequest(
                usageId, pricingVersion, 1_000L, 200L, 20L, 100L, 0L, billedQuota, now
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private long revenue(
            com.lumora.cloud.billing.web.BillingWebContracts.AdminBillingStatisticsResponse statistics,
            String currency
    ) {
        return statistics.revenueThisMonth().stream()
                .filter(item -> item.currency().equals(currency))
                .mapToLong(item -> item.amountMinor())
                .findFirst()
                .orElse(0L);
    }
}
