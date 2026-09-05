package com.lumora.cloud.billing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.lumora.cloud.api.billing.BillingContracts.*;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.domain.dto.order.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationRequest;
import com.lumora.cloud.billing.domain.dto.subscription.GrantSubscriptionRequest;
import com.lumora.cloud.billing.domain.enums.BillingHistoryScope;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.job.quota.ReservationExpiryJob;
import com.lumora.cloud.billing.mapper.quota.ReservationMapper;
import com.lumora.cloud.billing.mapper.usage.UsageRecordMapper;
import com.lumora.cloud.billing.service.*;
import com.lumora.cloud.billing.support.*;
import com.lumora.cloud.billing.utils.QuotaCycleCalculator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses only the explicitly supplied loopback test instance and a dedicated schema. */
@SpringJUnitConfig(IssueListMySqlRegressionTest.Config.class)
@EnabledIfEnvironmentVariable(named = "LUMORA_ISSUE_MYSQL_PORT", matches = "[0-9]+")
class IssueListMySqlRegressionTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.lumora.cloud.billing.mapper")
    @Import({BillingCatalogServiceImpl.class, SubscriptionServiceImpl.class, SettlementServiceImpl.class,
            WalletServiceImpl.class, PurchaseOrderServiceImpl.class, BillingHistoryServiceImpl.class,
            PlanModelSelectionService.class, PlanModelAccessService.class, QuotaBucketService.class,
            QuotaLedgerWriter.class, QuotaCycleCalculator.class})
    static class Config {
        @Bean DataSource dataSource() {
            String port = System.getenv("LUMORA_ISSUE_MYSQL_PORT");
            return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port
                    + "/lumora_issue_fix?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                    "root", "");
        }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource source) {
            return Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        }
        @Bean @DependsOn("flyway") SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setDataSource(source);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean CatalogClient catalogClient() { return mock(CatalogClient.class); }
        @Bean PaymentProperties paymentProperties() {
            return new PaymentProperties(true, Duration.ofMinutes(30), Duration.ofDays(30));
        }
    }

    @Autowired ISettlementService settlements;
    @Autowired ISubscriptionService subscriptions;
    @Autowired IBillingCatalogService catalog;
    @Autowired IPurchaseOrderService orders;
    @Autowired IBillingHistoryService history;
    @Autowired IWalletService wallets;
    @Autowired CatalogClient modelCatalog;
    @Autowired ReservationMapper reservations;
    @Autowired UsageRecordMapper usages;
    @Autowired JdbcTemplate sql;

    String suffix;
    Long userId;

    @BeforeEach void newUser() {
        suffix = UUID.randomUUID().toString().replace("-", "");
        userId = 9_000_000_000L + Math.abs((long) suffix.hashCode());
        when(modelCatalog.publishedModelReferences()).thenReturn(List.of(new PublishedModelReference("test-model", "Test")));
    }

    void grant(Instant startsAt) {
        var plan = plan(9900L);
        subscriptions.grant(new GrantSubscriptionRequest(userId, plan.planVersionId(), "grant-" + suffix,
                startsAt, startsAt.plus(30, ChronoUnit.DAYS)));
    }

    com.lumora.cloud.billing.domain.vo.plan.PlanResponse plan(long price) {
        return catalog.create(new CreatePlanRequest("test-" + suffix, "Test plan", "Isolated regression",
                price, "CNY", new BigDecimal("100"), List.of("test-model")));
    }

    String reserve(String ending, String quota) {
        String id = suffix + ending;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        settlements.reserve(new ReserveRequest(id, id, userId, "test-model", "v1",
                new BigDecimal(quota), now, BigDecimal.ONE, null, now.plusSeconds(600)));
        return id;
    }

    SettleRequest usage(String id, String amount) {
        return new SettleRequest("usage-" + id, "v1", 100, 20, 0, 0, 0, new BigDecimal(amount),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    ReconciliationRequest resolve(ReconciliationRequest.Action action, SettleRequest usage) {
        return new ReconciliationRequest(action, "supplier invoice TEST-001 verified", usage);
    }

    void bucket(String id, String reserved, String consumed) {
        var row = sql.queryForMap("""
                SELECT reserved_quota, consumed_quota FROM quota_bucket
                WHERE id = (SELECT quota_bucket_id FROM billing_reservation WHERE request_id = ?)
                """, id);
        assertThat((BigDecimal) row.get("reserved_quota")).isEqualByComparingTo(reserved);
        assertThat((BigDecimal) row.get("consumed_quota")).isEqualByComparingTo(consumed);
    }

    void summary(long pending, long completed, long failed, String billed) {
        var row = sql.queryForMap("SELECT * FROM billing_usage_daily_summary WHERE user_id = ?", userId);
        assertThat(((Number) row.get("request_count")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("pending_count")).longValue()).isEqualTo(pending);
        assertThat(((Number) row.get("completed_count")).longValue()).isEqualTo(completed);
        assertThat(((Number) row.get("failed_count")).longValue()).isEqualTo(failed);
        assertThat((BigDecimal) row.get("billed_quota")).isEqualByComparingTo(billed);
    }

    void audit(String id, String action, String prior) {
        var entries = sql.queryForList("""
                SELECT description FROM quota_ledger WHERE reference_type = 'RECONCILIATION' AND reference_id = ?
                """, String.class, id);
        assertThat(entries).singleElement().satisfies(text ->
                assertThat(text).contains("#42", action, "原记录费用=" + prior, "TEST-001"));
    }

    void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode()).isEqualTo(code);
    }

    @Test void lm005FManualOverageSettlementAndRetriesChargeOnce() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-overage", "10");
        var usage = usage(id, "15");
        assertThat(settlements.settle(id, usage).reservationStatus()).isEqualTo(ReservationStatus.PENDING_RECONCILIATION);
        summary(1, 0, 0, "15");
        var request = resolve(ReconciliationRequest.Action.SETTLE, usage);
        assertThat(settlements.reconcile(42L, id, request).reservation().getStatus()).isEqualTo("SETTLED");
        settlements.reconcile(42L, id, request);
        var replay = settlements.settle(id, usage);
        assertThat(replay.billedQuota()).isEqualByComparingTo("15");
        assertThat(replay.releasedQuota()).isZero();
        bucket(id, "0", "15");
        summary(0, 1, 0, "15");
        audit(id, "SETTLE", "15");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM billing_usage_record WHERE user_id = ?", Long.class, userId)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM quota_ledger WHERE entry_type = 'SETTLE' AND user_id = ?", Long.class, userId)).isEqualTo(1);
    }

    @Test void lm005FUnknownUsageCanBeReleasedWithEvidenceAndRetried() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-unknown", "10");
        settlements.markPending(id, new PendingRequest("supplier response lost"));
        code(() -> settlements.release(id, new ReleaseRequest("unprivileged release")), "RESERVATION_NOT_RELEASABLE");
        var request = resolve(ReconciliationRequest.Action.RELEASE, null);
        assertThat(settlements.reconcile(42L, id, request).reservation().getStatus()).isEqualTo("RELEASED");
        settlements.reconcile(42L, id, request);
        bucket(id, "0", "0");
        audit(id, "RELEASE", "0");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM billing_usage_record WHERE user_id = ?", Long.class, userId)).isZero();
    }

    @Test void lm005FWaivingDisputedUsageAlsoCorrectsDailySummary() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-waive", "10");
        settlements.settle(id, usage(id, "15"));
        var request = resolve(ReconciliationRequest.Action.RELEASE, null);
        var resolved = settlements.reconcile(42L, id, request);
        settlements.reconcile(42L, id, request);
        assertThat(resolved.usage().getStatus()).isEqualTo("FAILED");
        assertThat(resolved.usage().getBilledQuota()).isZero();
        bucket(id, "0", "0");
        summary(0, 0, 1, "0");
        audit(id, "RELEASE", "15");
    }

    @Test void lm005FManualSettlementPreservesOtherRequestsReservations() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-short", "10");
        String other = reserve("-other", "90");
        var usage = usage(id, "15");
        settlements.settle(id, usage);
        code(() -> settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.SETTLE, usage)),
                "INSUFFICIENT_RECONCILIATION_QUOTA");
        bucket(id, "100", "0");
        summary(1, 0, 0, "15");
        assertThat(settlements.reconciliation(id).reservation().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(settlements.reconciliation(other).reservation().getStatus()).isEqualTo("ACTIVE");
    }

    @Test void lm005FRejectsChangedUsageAndMissingReason() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-mismatch", "10");
        settlements.settle(id, usage(id, "15"));
        code(() -> settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.SETTLE, usage(id, "16"))),
                "USAGE_IDEMPOTENCY_CONFLICT");
        code(() -> settlements.reconcile(42L, id,
                new ReconciliationRequest(ReconciliationRequest.Action.RELEASE, " ", null)), "INVALID_REASON");
        bucket(id, "10", "0");
        summary(1, 0, 0, "15");
    }

    @Test void lm005FAnyAuditWriteFailureRollsBackMoneyUsageAndSummaries() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-rollback", "10");
        var usage = usage(id, "15");
        settlements.settle(id, usage);
        sql.update("""
                INSERT INTO quota_ledger (id, user_id, quota_bucket_id, reservation_id,
                    entry_type, reference_type, reference_id, description)
                SELECT ?, user_id, quota_bucket_id, id, 'ADJUSTMENT', 'RECONCILIATION', request_id, 'conflicting test entry'
                FROM billing_reservation WHERE request_id = ?
                """, UUID.randomUUID().toString(), id);
        assertThatThrownBy(() -> settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.SETTLE, usage)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        bucket(id, "10", "0");
        summary(1, 0, 0, "15");
        var pending = settlements.reconciliation(id);
        assertThat(pending.reservation().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(pending.usage().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM quota_ledger WHERE entry_type = 'SETTLE' AND user_id = ?", Long.class, userId)).isZero();
    }


    @Test void lm005FConcurrentAdminActionsHaveOnlyOneTerminalEffect() throws Exception {
        grant(Instant.now().minusSeconds(60));
        for (boolean conflicting : List.of(false, true)) {
            String id = reserve(conflicting ? "-race" : "-duplicate", "10");
            settlements.markPending(id, new PendingRequest("usage verification required"));
            var charge = usage(id, "1");
            var settle = resolve(ReconciliationRequest.Action.SETTLE, charge);
            var second = conflicting ? resolve(ReconciliationRequest.Action.RELEASE, null) : settle;
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var results = new java.util.ArrayList<java.util.concurrent.Future<String>>();
                for (var request : List.of(settle, second)) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return settlements.reconcile(42L, id, request).reservation().getStatus();
                        } catch (ApiException error) {
                            return error.getCode();
                        }
                    }));
                }
                assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                start.countDown();
                var outcomes = new java.util.ArrayList<String>();
                for (var result : results) outcomes.add(result.get(10, java.util.concurrent.TimeUnit.SECONDS));
                if (conflicting) {
                    assertThat(outcomes).contains("RESERVATION_NOT_PENDING");
                    assertThat(outcomes).anyMatch(status -> status.equals("SETTLED") || status.equals("RELEASED"));
                } else {
                    assertThat(outcomes).containsExactly("SETTLED", "SETTLED");
                }
            }
            assertThat(sql.queryForObject("""
                    SELECT COUNT(*) FROM quota_ledger WHERE reference_type = 'RECONCILIATION' AND reference_id = ?
                    """, Long.class, id)).isEqualTo(1);
            assertThat(sql.queryForObject("""
                    SELECT COUNT(*) FROM quota_ledger WHERE entry_type IN ('SETTLE','RELEASE') AND reservation_id =
                    (SELECT id FROM billing_reservation WHERE request_id = ?)
                    """, Long.class, id)).isEqualTo(1);
            assertThat(sql.queryForObject("""
                    SELECT reserved_quota FROM quota_bucket WHERE id =
                    (SELECT quota_bucket_id FROM billing_reservation WHERE request_id = ?)
                    """, BigDecimal.class, id)).isZero();
        }
    }

    @Test void lm013ZeroChargeCompletesAndReleasesFullReservationExactlyOnce() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-zero", "10");
        var usage = usage(id, "0");
        var settled = settlements.settle(id, usage);
        settlements.settle(id, usage);
        assertThat(settled.reservationStatus()).isEqualTo(ReservationStatus.SETTLED);
        assertThat(settled.releasedQuota()).isEqualByComparingTo("10");
        bucket(id, "0", "0");
        summary(0, 1, 0, "0");
    }

    @Test void lm007FAndLm016CrossPeriodSettlementStaysInOriginalBucket() throws Exception {
        Instant boundary = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.MICROS);
        grant(boundary.minus(7, ChronoUnit.DAYS));
        String id = reserve("-boundary", "10");
        var original = settlements.reconciliation(id).reservation();
        assertThat(original.getExpiresAt()).isAfter(boundary);
        Thread.sleep(Math.max(1, Duration.between(Instant.now(), boundary).toMillis() + 40));
        new ReservationExpiryJob(reservations, settlements, 100).releaseExpiredReservations();
        assertThat(settlements.reconciliation(id).reservation().getStatus()).isEqualTo("ACTIVE");
        settlements.settle(id, usage(id, "1"));
        var current = history.recent(userId, BillingHistoryScope.CURRENT_PERIOD);
        assertThat(current.usage()).isEmpty();
        assertThat(current.usageSummary().billedQuota()).isZero();
        assertThat(current.quotaSummary().consumedDelta()).isZero();
        assertThat(usages.findRecentByBucket(userId, original.getQuotaBucketId(), 10)).hasSize(1);
        assertThat(usages.aggregateUserByBucket(userId, original.getQuotaBucketId()).getBilledQuota()).isEqualByComparingTo("1");
        bucket(id, "0", "1");
    }

    @Test void legacyExpiredReleaseCanBeManuallyClosedWithoutDuplicateLedger() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-legacy", "10");
        settlements.release(id, new ReleaseRequest("预占超时，系统自动释放"));
        // Exact V9 conversion of a legacy expiry row, preserving its original RELEASE ledger.
        sql.update("UPDATE billing_reservation SET status = 'PENDING_RECONCILIATION', hold_released = TRUE WHERE request_id = ?", id);
        settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.RELEASE, null));
        settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.RELEASE, null));
        bucket(id, "0", "0");
        assertThat(settlements.reconciliation(id).reservation().getStatus()).isEqualTo("RELEASED");
    }

    @Test void expiredHoldReturnsCapacityAndLateUsageSettlesExactlyOnce() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-late", "10");
        var charge = usage(id, "3");
        sql.update("UPDATE billing_reservation SET expires_at = ? WHERE request_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), id);
        settlements.expire(id);
        settlements.expire(id);
        bucket(id, "0", "0");
        assertThat(settlements.reconciliation(id).reservation().isHoldReleased()).isTrue();
        assertThat(settlements.reconciliation(id).reservation().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        settlements.settle(id, charge);
        settlements.settle(id, charge);
        settlements.expire(id);
        bucket(id, "0", "3");
        summary(0, 1, 0, "3");
        assertThat(settlements.reconciliation(id).history()).hasSize(3);
    }

    @Test void lateUsageCannotConsumeOtherReservationsAndRemainsRecoverable() {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-deferred", "10");
        sql.update("UPDATE billing_reservation SET expires_at = ? WHERE request_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), id);
        settlements.expire(id);
        String other = reserve("-held", "100");
        var charge = usage(id, "3");
        assertThat(settlements.settle(id, charge).reservationStatus()).isEqualTo(ReservationStatus.PENDING_RECONCILIATION);
        settlements.settle(id, charge);
        settlements.autoReconcile(id);
        bucket(id, "100", "0");
        summary(1, 0, 0, "3");
        assertThat(settlements.reconciliation(id).reservation().getReconciliationAttempts()).isEqualTo(1);
        assertThat(settlements.reconciliation(id).reservation().getReconciliationNote()).contains("额度不足");
        settlements.release(other, new ReleaseRequest("supplier rejected"));
        assertThat(settlements.autoReconcile(id).reservation().getStatus()).isEqualTo("SETTLED");
        settlements.autoReconcile(id);
        bucket(id, "0", "3");
        summary(0, 1, 0, "3");
    }

    @Test void automaticReconciliationUsesRecordedEvidenceAndLeavesMissingEvidenceForAdmin() {
        grant(Instant.now().minusSeconds(60));
        String known = reserve("-known", "10");
        String missing = reserve("-missing", "10");
        settlements.settle(known, usage(known, "12"));
        settlements.markPending(missing, new PendingRequest("No usage"));
        new com.lumora.cloud.billing.job.quota.ReconciliationJob(reservations, settlements, 1000).reconcileDue();
        assertThat(settlements.reconciliation(known).reservation().getStatus()).isEqualTo("SETTLED");
        assertThat(settlements.reconciliation(missing).reservation().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(settlements.reconciliation(missing).reservation().getReconciliationNote()).contains("缺少可靠用量");
        assertThat(sql.queryForList("SELECT description FROM quota_ledger WHERE reference_type = 'RECONCILIATION' AND reference_id = ?",
                String.class, known)).singleElement().satisfies(text -> assertThat(text).contains("系统自动", "usage-", "12"));
        var page = settlements.listReconciliation("PENDING_RECONCILIATION", null, userId, 1, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().reservation().getRequestId()).isEqualTo(missing);
        assertThat(reservations.findDueReconciliation(Instant.now(), 1000)).doesNotContain(missing);
    }

    @Test void automaticAndManualReconciliationHaveOneAtomicTerminalEffect() throws Exception {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-auto-race", "10");
        settlements.settle(id, usage(id, "12"));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var automatic = pool.submit(() -> { start.await(); return settlements.autoReconcile(id).reservation().getStatus(); });
            var manual = pool.submit(() -> {
                start.await();
                try { return settlements.reconcile(42L, id, resolve(ReconciliationRequest.Action.RELEASE, null)).reservation().getStatus(); }
                catch (ApiException error) { return error.getCode(); }
            });
            start.countDown();
            var automaticStatus = automatic.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var manualStatus = manual.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(automaticStatus).isIn("SETTLED", "RELEASED");
            assertThat(manualStatus).isIn("RELEASED", "RESERVATION_NOT_PENDING");
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM quota_ledger WHERE reference_type = 'RECONCILIATION' AND reference_id = ?",
                Long.class, id)).isEqualTo(1);
        String terminal = settlements.reconciliation(id).reservation().getStatus();
        bucket(id, "0", terminal.equals("SETTLED") ? "12" : "0");
        summary(0, terminal.equals("SETTLED") ? 1 : 0, terminal.equals("SETTLED") ? 0 : 1, terminal.equals("SETTLED") ? "12" : "0");
    }

    @Test void expiryAndSettlementCanRaceWithoutDoubleReturningFunds() throws Exception {
        grant(Instant.now().minusSeconds(60));
        String id = reserve("-expiry-race", "10");
        sql.update("UPDATE billing_reservation SET expires_at = ? WHERE request_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), id);
        var charge = usage(id, "3");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var expiry = pool.submit(() -> { start.await(); settlements.expire(id); return true; });
            var settlement = pool.submit(() -> { start.await(); return settlements.settle(id, charge); });
            start.countDown();
            expiry.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(settlement.get(10, java.util.concurrent.TimeUnit.SECONDS).reservationStatus()).isEqualTo(ReservationStatus.SETTLED);
        }
        bucket(id, "0", "3");
        summary(0, 1, 0, "3");
    }

    @Test void renewalsExposeOrderedDatesAndOrderOwnership() {
        var plan = plan(0);
        var first = orders.walletPay(userId, orders.create(userId, "renew-1", new CreatePurchaseOrderRequest(plan.planVersionId())).orderNo());
        var second = orders.walletPay(userId, orders.create(userId, "renew-2", new CreatePurchaseOrderRequest(plan.planVersionId())).orderNo());
        var third = orders.walletPay(userId, orders.create(userId, "renew-3", new CreatePurchaseOrderRequest(plan.planVersionId())).orderNo());
        var overview = subscriptions.overview(userId);
        assertThat(overview.scheduledSubscriptions()).hasSize(2);
        assertThat(overview.scheduledSubscriptions().getFirst().subscription().subscriptionId()).isEqualTo(second.subscriptionId());
        assertThat(overview.scheduledSubscriptions().getLast().subscription().subscriptionId()).isEqualTo(third.subscriptionId());
        assertThat(second.subscription().startsAt()).isEqualTo(first.subscription().endsAt());
        assertThat(third.subscription().startsAt()).isEqualTo(second.subscription().endsAt());
        assertThat(subscriptions.forUser(userId + 1, second.subscriptionId())).isNull();
    }

    @Test void upcomingEntitlementIsVisibleWithoutCurrentSubscription() {
        var plan = plan(0);
        Instant begins = Instant.now().plusSeconds(86400);
        subscriptions.grant(new GrantSubscriptionRequest(userId, plan.planVersionId(), "future-" + suffix, begins, begins.plusSeconds(86400)));
        var overview = subscriptions.overview(userId);
        assertThat(overview.hasActiveSubscription()).isFalse();
        assertThat(overview.scheduledSubscriptions()).singleElement().satisfies(item -> assertThat(item.plan().name()).isEqualTo("Test plan"));
    }

    @Test void batchKeepsSuccessWhenAnotherItemLacksEvidenceAndCanBeRetried() {
        grant(Instant.now().minusSeconds(60));
        String known = reserve("-batch-known", "10");
        String missing = reserve("-batch-missing", "10");
        settlements.settle(known, usage(known, "12"));
        settlements.markPending(missing, new PendingRequest("No usage"));
        var access = mock(com.lumora.cloud.billing.security.BillingAccess.class);
        when(access.requireAdminUserId()).thenReturn(42L);
        var controller = new com.lumora.cloud.billing.controller.admin.BillingReconciliationController(access, settlements);
        var batch = new com.lumora.cloud.billing.domain.dto.reconciliation.ReconciliationBatchRequest(
                List.of(known, missing), ReconciliationRequest.Action.SETTLE, "supplier TEST-001 verified");
        var results = controller.batch(batch);
        assertThat(results.getFirst().completed()).isTrue();
        assertThat(results.getLast().code()).isEqualTo("EVIDENCE_REQUIRED");
        var retry = controller.batch(batch);
        assertThat(retry.getFirst().completed()).isTrue();
        bucket(known, "10", "12");
        assertThat(settlements.reconciliation(missing).reservation().getStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(settlements.reconciliation(known).history()).anySatisfy(entry -> assertThat(entry.getDescription()).contains("#42", "TEST-001"));
    }

    @Test void lm012FreePlanNeedsNoWalletAndGrantsOnlyOneSubscription() {
        var plan = plan(0);
        var order = orders.create(userId, "free-" + suffix, new CreatePurchaseOrderRequest(plan.planVersionId()));
        var paid = orders.walletPay(userId, order.orderNo());
        var replay = orders.walletPay(userId, order.orderNo());
        assertThat(paid.status()).isEqualTo("FULFILLED");
        assertThat(replay.subscriptionId()).isEqualTo(paid.subscriptionId());
        assertThat(subscriptions.overview(userId).hasActiveSubscription()).isTrue();
        assertThat(wallets.overview(userId).ledger()).isEmpty();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE user_id = ?", Long.class, userId)).isEqualTo(1);
    }
}
