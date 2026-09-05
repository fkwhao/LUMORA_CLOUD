package com.lumora.cloud.billing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.billing.BillingContracts.*;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.billing.config.PaymentProperties;
import com.lumora.cloud.billing.domain.entity.quota.*;
import com.lumora.cloud.billing.domain.entity.usage.UsageRecordEntity;
import com.lumora.cloud.billing.domain.entity.plan.*;
import com.lumora.cloud.billing.domain.entity.order.PurchaseOrderEntity;
import com.lumora.cloud.billing.domain.entity.account.BillingAccountEntity;
import com.lumora.cloud.billing.domain.entity.subscription.SubscriptionEntity;
import com.lumora.cloud.billing.domain.dto.order.CreatePurchaseOrderRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.enums.BillingHistoryScope;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.mapper.quota.*;
import com.lumora.cloud.billing.mapper.usage.*;
import com.lumora.cloud.billing.mapper.plan.*;
import com.lumora.cloud.billing.mapper.order.*;
import com.lumora.cloud.billing.mapper.wallet.*;
import com.lumora.cloud.billing.mapper.account.BillingAccountMapper;
import com.lumora.cloud.billing.mapper.subscription.SubscriptionMapper;
import com.lumora.cloud.billing.service.*;
import com.lumora.cloud.billing.support.*;
import com.lumora.cloud.billing.job.quota.ReservationExpiryJob;
import com.lumora.cloud.billing.error.ApiException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IssueListBillingReproductionTest {
    static final PaymentProperties PAYMENTS=new PaymentProperties(false,Duration.ofMinutes(30),Duration.ofDays(30));
    static void set(Object target,String field,Object value){ReflectionTestUtils.setField(target,field,value);}
    static SettleRequest usage(String id,Instant at){return new SettleRequest("usage-"+id,"v1",100,20,0,0,0,BigDecimal.ONE,at);}
    static String errorOf(Runnable action){try{action.run();return "NONE";}catch(ApiException e){return e.getCode();}}

    static class SettlementFixture {
        final ReservationMapper reservations=mock(ReservationMapper.class);
        final QuotaBucketMapper buckets=mock(QuotaBucketMapper.class);
        final UsageRecordMapper usages=mock(UsageRecordMapper.class);
        final UsageDailySummaryMapper daily=mock(UsageDailySummaryMapper.class);
        final QuotaBucketService bucketService=mock(QuotaBucketService.class);
        final ISubscriptionService subscriptions=mock(ISubscriptionService.class);
        final QuotaBucketEntity bucket=QuotaBucketEntity.create("old-bucket","subscription",7L,1,Instant.now().minusSeconds(100),Instant.now().plusSeconds(3600),new BigDecimal("100"));
        final QuotaLedgerWriter ledger=mock(QuotaLedgerWriter.class);
        final SettlementServiceImpl service=new SettlementServiceImpl(subscriptions,bucketService,buckets,reservations,usages,daily,ledger,mock(PlanModelAccessService.class));
        ReservationEntity row;
        UsageRecordEntity storedUsage;
        SettlementFixture(){
            var subscription=mock(SubscriptionEntity.class);
            when(subscription.getPlanVersionId()).thenReturn(20L);
            when(subscriptions.activeForUpdate(eq(7L),any())).thenReturn(subscription);
            when(bucketService.currentForUpdate(any(),any())).thenReturn(bucket);
            when(buckets.findByIdForUpdate("old-bucket")).thenReturn(bucket);
            when(buckets.reserve(anyString(),any())).thenAnswer(i->{set(bucket,"reservedQuota",i.getArgument(1));return 1;});
            when(reservations.insertIdempotent(any())).thenAnswer(i->{row=i.getArgument(0);return 1;});
            when(reservations.findByRequestIdForUpdate(anyString())).thenAnswer(i->row);
            when(reservations.findByRequestId(anyString())).thenAnswer(i->row);
            when(reservations.activate(anyString(),anyString(),any())).thenAnswer(i->{set(row,"status","ACTIVE");set(row,"quotaBucketId",i.getArgument(1));set(row,"expiresAt",i.getArgument(2));return 1;});
            when(reservations.markPending(anyString(),anyString())).thenAnswer(i->{set(row,"status","PENDING_RECONCILIATION");return 1;});
            when(reservations.expireHold(anyString())).thenAnswer(i->{set(row,"status","PENDING_RECONCILIATION");set(row,"holdReleased",true);return 1;});
            when(reservations.markReleased(anyString(),anyString(),any())).thenAnswer(i->{set(row,"status","RELEASED");return 1;});
            when(reservations.markSettled(anyString(),any(),any())).thenAnswer(i->{set(row,"status","SETTLED");set(row,"settledQuota",i.getArgument(1));return 1;});
            when(buckets.release(anyString(),any())).thenAnswer(i->{set(bucket,"reservedQuota",BigDecimal.ZERO);return 1;});
            when(buckets.settle(anyString(),any(),any())).thenAnswer(i->{set(bucket,"reservedQuota",BigDecimal.ZERO);set(bucket,"consumedQuota",i.getArgument(2));return 1;});
            when(buckets.settleReconciled(anyString(),any(),any())).thenAnswer(i->{
                BigDecimal reserved=i.getArgument(1), billed=i.getArgument(2);
                if(bucket.getReservedQuota().compareTo(reserved)<0
                        || bucket.availableQuota().add(reserved).compareTo(billed)<0) return 0;
                set(bucket,"reservedQuota",bucket.getReservedQuota().subtract(reserved));
                set(bucket,"consumedQuota",bucket.getConsumedQuota().add(billed));return 1;
            });
            when(usages.insertIdempotent(any())).thenAnswer(i->{if(storedUsage==null) storedUsage=i.getArgument(0);return 1;});
            when(usages.findByUsageIdForUpdate(anyString())).thenAnswer(i->storedUsage);
            when(usages.findByReservationIdForUpdate(anyString())).thenAnswer(i->storedUsage);
            when(usages.findByReservationId(anyString())).thenAnswer(i->storedUsage);
            when(usages.markPending(anyString())).thenAnswer(i->{set(storedUsage,"status","PENDING_RECONCILIATION");return 1;});
            when(usages.markWaived(anyString())).thenAnswer(i->{set(storedUsage,"status","FAILED");set(storedUsage,"billedQuota",BigDecimal.ZERO);return 1;});
            when(daily.resolvePending(any(),anyString(),any())).thenReturn(1);
            when(usages.markCompleted(anyString())).thenAnswer(i->{set(storedUsage,"status","COMPLETED");return 1;});
            when(daily.addUsage(any(),anyString(),any())).thenReturn(1);
            when(reservations.findExpiredActiveRequestIds(any(),anyInt())).thenAnswer(i->row!=null && "ACTIVE".equals(row.getStatus()) && !row.getExpiresAt().isAfter(i.getArgument(0,Instant.class))?List.of(row.getRequestId()):List.of());
        }
        void active(String id,Instant expiry){
            row=ReservationEntity.processing("reservation-"+id,id,"client-"+id,7L,"test-model","v1",BigDecimal.TEN,Instant.now(),BigDecimal.ONE,null,expiry);
            set(row,"status","ACTIVE");set(row,"quotaBucketId","old-bucket");set(bucket,"reservedQuota",BigDecimal.TEN);
        }
    }

    @Test void lm005FAuthoritativeLateUsageClosesPendingReservationExactlyOnce(){
        var f=new SettlementFixture();f.active("lm005",Instant.now().plusSeconds(60));
        f.service.markPending("lm005",new PendingRequest("missing supplier usage"));
        String settle=errorOf(()->f.service.settle("lm005",usage("lm005",Instant.now())));
        String release=errorOf(()->f.service.release("lm005",new ReleaseRequest("verified free")));
        System.out.println("REPRO LM-005-F settle="+settle+" release="+release+" final="+f.row.getStatus());
        assertThat(settle).isEqualTo("NONE");
        assertThat(release).isEqualTo("RESERVATION_NOT_RELEASABLE");
        assertThat(f.row.getStatus()).isEqualTo("SETTLED");
        assertThat(f.bucket.getReservedQuota()).isZero();
        f.service.settle("lm005",usage("lm005",Instant.now()));
        assertThat(f.bucket.getConsumedQuota()).isEqualByComparingTo("1");
        verify(f.buckets,times(1)).settle(anyString(),any(),any());
        verify(f.daily,times(1)).addUsage(any(),eq("COMPLETED"),any());
    }

    @Test void lm007FCrossPeriodActiveRequestRetainsSettlementEligibility() throws Exception {
        var f=new SettlementFixture();
        Instant boundary=Instant.now().plusMillis(500);set(f.bucket,"endsAt",boundary);
        var reserved=f.service.reserve(new ReserveRequest("lm007","client-lm007",7L,"test-model","v1",BigDecimal.TEN,Instant.now(),BigDecimal.ONE,null,Instant.now().plusSeconds(60)));
        assertThat(reserved.expiresAt()).isAfter(boundary);
        Thread.sleep(Math.max(1,Duration.between(Instant.now(),boundary).toMillis()+40));
        new ReservationExpiryJob(f.reservations,f.service,100).releaseExpiredReservations();
        String statusAfterScan=f.row.getStatus();
        assertThat(statusAfterScan).isEqualTo("ACTIVE");
        String result=errorOf(()->f.service.settle("lm007",usage("lm007",Instant.now())));
        System.out.println("FIX LM-007-F requestedExpiry=60s effectiveExpiry="+reserved.expiresAt()+" upstreamStillRunning=true statusAfterScan="+statusAfterScan+" settlement="+result);
        assertThat(result).isEqualTo("NONE");
        assertThat(f.row.getQuotaBucketId()).isEqualTo("old-bucket");
        assertThat(f.bucket.getConsumedQuota()).isEqualByComparingTo("1");
    }

    @Test void lm007RAfterOutageDelayedUsageSettlesAfterExpiryScan(){
        var f=new SettlementFixture();f.active("lm007r",Instant.now().minusSeconds(3600));
        new ReservationExpiryJob(f.reservations,f.service,100).releaseExpiredReservations();
        String result=errorOf(()->f.service.settle("lm007r",usage("lm007r",Instant.now().minusSeconds(3600))));
        System.out.println("REPRO LM-007-R statusAfterScan="+f.row.getStatus()+" delayedSettlement="+result);
        assertThat(result).isEqualTo("NONE");
        assertThat(f.row.getStatus()).isEqualTo("SETTLED");
        assertThat(f.row.isHoldReleased()).isTrue();
        assertThat(f.bucket.getReservedQuota()).isZero();
        assertThat(f.bucket.getConsumedQuota()).isEqualByComparingTo("1");
    }

    static class CatalogFixture {
        final BillingPlanMapper plans=mock(BillingPlanMapper.class);
        final PlanVersionMapper versions=mock(PlanVersionMapper.class);
        final PlanVersionModelMapper models=mock(PlanVersionModelMapper.class);
        final CatalogClient modelCatalog=mock(CatalogClient.class);
        final PlanModelSelectionService selection=new PlanModelSelectionService(modelCatalog);
        final BillingCatalogServiceImpl service=new BillingCatalogServiceImpl(plans,versions,models,selection);
        BillingPlanEntity plan;PlanVersionEntity version;
        CatalogFixture(){
            when(modelCatalog.publishedModelReferences()).thenReturn(List.of(new PublishedModelReference("model-a","A")));
            when(plans.insert(any(BillingPlanEntity.class))).thenAnswer(i->{plan=i.getArgument(0);set(plan,"id",10L);return 1;});
            when(versions.insert(any(PlanVersionEntity.class))).thenAnswer(i->{version=i.getArgument(0);set(version,"id",20L);return 1;});
            when(plans.selectList(any())).thenAnswer(i->plan==null?List.of():List.of(plan));
            when(plans.selectById(10L)).thenAnswer(i->plan);
            when(versions.findLatestPublished(10L)).thenAnswer(i->version);
            when(versions.findPublishedById(20L)).thenAnswer(i->version);
            when(models.findModelCodes(20L)).thenReturn(List.of("model-a"));
        }
        PlanResponse create(long price){
            var request=new CreatePlanRequest("repro-plan","Repro Plan","test",price,"CNY",new BigDecimal("100"),List.of("model-a"));
            try(var validators=Validation.buildDefaultValidatorFactory()){assertThat(validators.getValidator().validate(request)).isEmpty();}
            return service.create(request);
        }
    }
    static class OrderFixture {
        final PurchaseOrderMapper orders=mock(PurchaseOrderMapper.class);
        final ISubscriptionService subscriptions=mock(ISubscriptionService.class);
        final PurchaseOrderServiceImpl service;
        PurchaseOrderEntity row;
        OrderFixture(IBillingCatalogService catalog,IWalletService wallet){
            service=new PurchaseOrderServiceImpl(orders,mock(PaymentAttemptMapper.class),catalog,subscriptions,wallet,PAYMENTS,mock(ApplicationEventPublisher.class));
            when(orders.insertPendingIgnore(any())).thenAnswer(i->{row=i.getArgument(0);return 1;});
            when(orders.findByIdempotencyForUpdate(eq(7L),anyString())).thenAnswer(i->row);
            when(orders.findByOrderNoForUpdate(anyString())).thenAnswer(i->row);
            var granted=mock(com.lumora.cloud.billing.domain.vo.subscription.SubscriptionResponse.class);
            when(granted.subscriptionId()).thenReturn("sub-paid");
            when(subscriptions.purchase(eq(7L),eq(20L),anyString(),any())).thenReturn(granted);
            when(orders.markFulfilled(anyString(),any(),anyString(),anyString())).thenAnswer(i->{
                set(row,"status","FULFILLED");set(row,"subscriptionId",i.getArgument(2));
                set(row,"paymentProvider",i.getArgument(3));return 1;
            });
            when(orders.selectById(any())).thenAnswer(i->row);
        }
    }

    @Test void lm012FreePlanIsFulfilledWithoutWalletAndReplayDoesNotGrantTwice(){
        var catalog=new CatalogFixture();var plan=catalog.create(0);
        var accounts=mock(WalletAccountMapper.class);var ledgers=mock(WalletLedgerMapper.class);
        var wallet=new WalletServiceImpl(accounts,mock(WalletTopupOrderMapper.class),ledgers,PAYMENTS,mock(ApplicationEventPublisher.class));
        var orders=new OrderFixture(catalog.service,wallet);
        var order=orders.service.create(7L,"lm012",new CreatePurchaseOrderRequest(plan.planVersionId()));
        String result=errorOf(()->orders.service.walletPay(7L,order.orderNo()));
        System.out.println("REPRO LM-012 allowedPrice="+plan.monthlyPriceMinor()+" orderAmount="+order.amountMinor()+" paymentError="+result);
        assertThat(result).isEqualTo("NONE");
        assertThat(orders.service.walletPay(7L,order.orderNo()).status()).isEqualTo("FULFILLED");
        verify(orders.subscriptions,times(1)).purchase(eq(7L),eq(20L),anyString(),any());
        verifyNoInteractions(accounts,ledgers);
    }

    @Test void lm014DisabledOnlyModelBlocksListingCreationAndPayment(){
        var catalog=new CatalogFixture();var plan=catalog.create(9900);
        var wallet=mock(IWalletService.class);
        var orders=new OrderFixture(catalog.service,wallet);
        assertThat(catalog.service.listPublished()).hasSize(1);
        var order=orders.service.create(7L,"lm014",new CreatePurchaseOrderRequest(plan.planVersionId()));
        when(catalog.modelCatalog.publishedModelReferences()).thenReturn(List.of());
        var offers=catalog.service.listPublished();
        String create=errorOf(()->orders.service.create(7L,"lm014-new",new CreatePurchaseOrderRequest(plan.planVersionId())));
        String pay=errorOf(()->orders.service.walletPay(7L,order.orderNo()));
        System.out.println("FIX LM-014 availableModels=0 offeredPlans="+offers.size()+" newOrder="+create+" existingOrderPayment="+pay);
        assertThat(offers).isEmpty();
        assertThat(create).isEqualTo("PLAN_HAS_NO_AVAILABLE_MODELS");
        assertThat(pay).isEqualTo("PLAN_HAS_NO_AVAILABLE_MODELS");
        assertThat(catalog.service.listAllPublished()).hasSize(1);
        assertThat(catalog.service.publishedVersion(plan.planVersionId())).isNotNull();
        verifyNoInteractions(wallet,orders.subscriptions);
    }

    @Test void lm016OldBucketChargeIsExcludedFromNewPeriodHistory(){
        var f=new SettlementFixture();Instant boundary=Instant.now().minusSeconds(1);
        set(f.bucket,"endsAt",boundary);f.active("lm016",boundary);
        f.service.settle("lm016",usage("lm016",Instant.now()));
        var newBucket=QuotaBucketEntity.create("new-bucket","subscription",7L,2,boundary,boundary.plusSeconds(3600),new BigDecimal("100"));
        var ledger=mock(QuotaLedgerMapper.class);var subscriptions=mock(SubscriptionMapper.class);
        var bucketService=mock(QuotaBucketService.class);var subscription=mock(SubscriptionEntity.class);
        when(subscriptions.findActiveForUpdate(eq(7L),any())).thenReturn(subscription);
        when(bucketService.currentForUpdate(any(),any())).thenReturn(newBucket);
        when(f.usages.findRecentByBucket(eq(7L),anyString(),anyInt())).thenAnswer(i->
            f.row.getQuotaBucketId().equals(i.getArgument(1))?List.of(f.storedUsage):List.of());
        when(f.usages.aggregateUserByBucket(eq(7L),anyString())).thenAnswer(i->{
            var aggregate=new com.lumora.cloud.billing.domain.projection.history.UsageAggregate();
            boolean same=f.row.getQuotaBucketId().equals(i.getArgument(1));
            set(aggregate,"requestCount",same?1L:0L);
            set(aggregate,"completedCount",same?1L:0L);
            set(aggregate,"billedQuota",same?f.storedUsage.getBilledQuota():BigDecimal.ZERO);
            return aggregate;
        });
        var history=new BillingHistoryServiceImpl(ledger,mock(QuotaDailySummaryMapper.class),f.usages,f.daily,subscriptions,bucketService);
        var shown=history.recent(7L,BillingHistoryScope.CURRENT_PERIOD);
        System.out.println("FIX LM-016 oldConsumed="+f.bucket.getConsumedQuota()+" newConsumed="+newBucket.getConsumedQuota()+" newPeriodUsageCount="+shown.usage().size());
        assertThat(shown.usage()).isEmpty();
        assertThat(shown.usageSummary().billedQuota()).isZero();
        when(bucketService.currentForUpdate(any(),any())).thenReturn(f.bucket);
        var oldHistory=history.recent(7L,BillingHistoryScope.CURRENT_PERIOD);
        assertThat(oldHistory.usage()).singleElement().satisfies(record->assertThat(record.billedQuota()).isEqualByComparingTo("1"));
        assertThat(oldHistory.usageSummary().billedQuota()).isEqualByComparingTo("1");
    }

    @Test void lm015RenewalAndDatesAppearInUserOverview() throws Exception{
        var accounts=mock(BillingAccountMapper.class);var subscriptions=mock(SubscriptionMapper.class);
        var catalog=mock(IBillingCatalogService.class);var buckets=mock(QuotaBucketService.class);
        var account=BillingAccountEntity.create(7L);set(account,"id",1L);
        when(accounts.findByUserIdForUpdate(7L)).thenReturn(account);
        Instant now=Instant.now();
        var current=SubscriptionEntity.purchase("current",1L,7L,20L,"old-order",now.minusSeconds(100),now.plusSeconds(86400));
        when(subscriptions.findLatestEndingForUpdate(eq(7L),any())).thenReturn(current);
        when(subscriptions.findActiveForUpdate(eq(7L),any())).thenReturn(current);
        AtomicReference<SubscriptionEntity> inserted=new AtomicReference<>();
        when(subscriptions.insert(any(SubscriptionEntity.class))).thenAnswer(i->{inserted.set(i.getArgument(0));return 1;});
        when(subscriptions.selectById(any())).thenAnswer(i->inserted.get());
        when(catalog.publishedVersion(20L)).thenReturn(new PlanResponse(10L,"pro","Pro","test",20L,1,9900L,"CNY",BigDecimal.TEN,"SELECTED",List.of("model-a")));
        when(buckets.currentForUpdate(any(),any())).thenReturn(QuotaBucketEntity.create("bucket","current",7L,1,now.minusSeconds(100),now.plusSeconds(86400),BigDecimal.TEN));
        var service=new SubscriptionServiceImpl(accounts,subscriptions,catalog,buckets,PAYMENTS);
        var renewal=service.purchase(7L,20L,"new-order",now);
        when(subscriptions.findScheduled(eq(7L),any())).thenAnswer(i->List.of(inserted.get()));
        var overview=service.overview(7L);
        assertThat(renewal.startsAt()).isEqualTo(current.getEndsAt());
        System.out.println("REPRO LM-015 renewal="+renewal.subscriptionId()+" starts="+renewal.startsAt()+" overviewSubscription="+overview.subscription().subscriptionId());
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(overview);
        assertThat(json).contains(renewal.subscriptionId(), "scheduledSubscriptions", "new-order");
        assertThat(overview.scheduledSubscriptions()).singleElement().satisfies(item -> {
            assertThat(item.subscription().startsAt()).isEqualTo(renewal.startsAt());
            assertThat(item.subscription().endsAt()).isEqualTo(renewal.endsAt());
        });
    }

    @Test void lm003BackendDeduplicatesSameKeyButCreditsAgainForNewKey(){
        var accounts=mock(WalletAccountMapper.class);
        var ledgers=mock(WalletLedgerMapper.class);
        var account=new com.lumora.cloud.billing.domain.entity.wallet.WalletAccountEntity();
        set(account,"id",30L);set(account,"userId",7L);set(account,"currency","CNY");
        set(account,"availableMinor",0L);set(account,"version",0L);
        var entries=new LinkedHashMap<String,com.lumora.cloud.billing.domain.entity.wallet.WalletLedgerEntity>();
        when(accounts.findForUpdate(7L,"CNY")).thenReturn(account);
        when(accounts.selectById(any())).thenReturn(account);
        when(accounts.credit(anyLong(),anyLong())).thenAnswer(i->{set(account,"availableMinor",account.getAvailableMinor()+i.getArgument(1,Long.class));return 1;});
        when(ledgers.findByReference(anyString(),anyString(),anyString())).thenAnswer(i->entries.values().stream().filter(e->e.getReferenceId().equals(i.getArgument(2))).findFirst().orElse(null));
        when(ledgers.insert(any(com.lumora.cloud.billing.domain.entity.wallet.WalletLedgerEntity.class))).thenAnswer(i->{var row=i.getArgument(0,com.lumora.cloud.billing.domain.entity.wallet.WalletLedgerEntity.class);entries.put(row.getId(),row);return 1;});
        when(ledgers.selectById(any())).thenAnswer(i->entries.get(i.getArgument(0)));
        var service=new WalletServiceImpl(accounts,mock(WalletTopupOrderMapper.class),ledgers,PAYMENTS,mock(ApplicationEventPublisher.class));
        var adjustment=new com.lumora.cloud.billing.domain.dto.wallet.AdminWalletAdjustmentRequest(7L,10000L,"CNY","reproduction");
        service.adjust(1L,"operation-1",adjustment);
        service.adjust(1L,"operation-1",adjustment);
        long afterSameKey=account.getAvailableMinor();
        service.adjust(1L,"operation-2",adjustment);
        System.out.println("REPRO LM-003 backend sameKeyRetryBalance="+afterSameKey+" newKeyRetryBalance="+account.getAvailableMinor()+" ledgerEntries="+entries.size());
        assertThat(afterSameKey).isEqualTo(10000L);
        assertThat(account.getAvailableMinor()).isEqualTo(20000L);
        assertThat(entries).hasSize(2);
    }

}
