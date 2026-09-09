package com.lumora.cloud.billing.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.billing.cache.PlanConfigurationCache.ModelAccess;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanConfigurationCacheTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Map<String, String> data = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(5);
    private final PlanConfigurationCache cache = new PlanConfigurationCache(redis, new ObjectMapper(), transactions, ttl);

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> data.get(i.<String>getArgument(0)));
        when(values.setIfAbsent(anyString(), anyString())).thenAnswer(i ->
                data.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        doAnswer(i -> { data.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString());
        doAnswer(i -> { data.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
    }

    @Test
    void cachesListAndVersionAsJsonWithoutChangingAmountsOrModelScope() {
        var plan = plan(20L);
        assertThat(cache.published(() -> List.of(plan))).containsExactly(plan);
        assertThat(cache.published(() -> { throw new AssertionError("unexpected MySQL read"); }))
                .containsExactly(plan);
        assertThat(cache.version(20L, () -> plan)).isEqualTo(plan);
        assertThat(cache.version(20L, () -> { throw new AssertionError("unexpected MySQL read"); }))
                .isEqualTo(plan);
        verify(values, times(2)).set(anyString(), anyString(), eq(ttl));
        verify(transactions, times(2)).getTransaction(argThat(def ->
                def.isReadOnly() && def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
    }

    @Test
    void keepsPurchasedVersionPermissionsSeparateFromNewVersions() {
        cache.modelAccess(20L, () -> new ModelAccess("SELECTED", List.of("model-a")));
        cache.modelAccess(21L, () -> new ModelAccess("SELECTED", List.of("model-b")));
        assertThat(cache.modelAccess(20L, () -> null).modelCodes()).containsExactly("model-a");
        assertThat(cache.modelAccess(21L, () -> null).modelCodes()).containsExactly("model-b");
    }

    @Test
    void cachesEmptyPublishedList() {
        cache.published(List::of);
        assertThat(cache.published(() -> { throw new AssertionError("unexpected MySQL read"); })).isEmpty();
    }

    @Test
    void invalidatesAllKindsOnlyAfterCommit() {
        warm();
        String before = data.get(PlanConfigurationCache.GENERATION_KEY);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit();
            assertThat(data.get(PlanConfigurationCache.GENERATION_KEY)).isEqualTo(before);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(data.get(PlanConfigurationCache.GENERATION_KEY)).isNotEqualTo(before);
        assertThat(cache.published(() -> List.of(plan(21L)))).containsExactly(plan(21L));
        assertThat(cache.version(20L, () -> plan(22L))).isEqualTo(plan(22L));
        assertThat(cache.modelAccess(20L, () -> new ModelAccess("SELECTED", List.of("model-b"))).modelCodes())
                .containsExactly("model-b");
    }

    @Test
    void rollbackLeavesExistingCacheValid() {
        warm();
        String before = data.get(PlanConfigurationCache.GENERATION_KEY);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit();
            TransactionSynchronizationManager.getSynchronizations().forEach(s ->
                    s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(data.get(PlanConfigurationCache.GENERATION_KEY)).isEqualTo(before);
        assertThat(cache.version(20L, () -> null)).isEqualTo(plan(20L));
    }

    @Test
    void transactionVersionFillWaitsForCommitAndReusesCallerConnection() {
        beginCallerTransaction();
        try {
            cache.version(20L, () -> plan(20L));
            cache.modelAccess(20L, () -> new ModelAccess("SELECTED", List.of("model-a")));
            verify(values, never()).set(anyString(), anyString(), any(Duration.class));
            verify(transactions, times(2)).getTransaction(argThat(def ->
                    def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            endCallerTransaction();
        }
        assertThat(cache.version(20L, () -> null)).isEqualTo(plan(20L));
        assertThat(cache.modelAccess(20L, () -> null).modelCodes()).containsExactly("model-a");
    }

    @Test
    void rollbackDoesNotPublishUncommittedVersion() {
        beginCallerTransaction();
        try {
            cache.version(20L, () -> plan(20L));
            TransactionSynchronizationManager.getSynchronizations().forEach(s ->
                    s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            endCallerTransaction();
        }
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.version(20L, () -> null)).isNull();
    }

    @Test
    void oldTransactionListIsNeverFilledEvenAfterCommit() {
        beginCallerTransaction();
        try {
            assertThat(cache.published(() -> List.of(plan(19L)))).containsExactly(plan(19L));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            endCallerTransaction();
        }
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.published(() -> List.of(plan(20L)))).containsExactly(plan(20L));
    }

    @Test
    void invalidationBeforeCommitDiscardsPendingVersionFill() {
        beginCallerTransaction();
        try {
            cache.version(20L, () -> plan(20L));
            data.put(PlanConfigurationCache.GENERATION_KEY, "new-generation");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            endCallerTransaction();
        }
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void retriesWhenPublishingInvalidatesAnInFlightRead() {
        AtomicInteger loads = new AtomicInteger();
        var result = cache.published(() -> {
            if (loads.incrementAndGet() == 1) {
                cache.evictAfterCommit();
                return List.of(plan(20L));
            }
            return List.of(plan(21L));
        });
        assertThat(result).containsExactly(plan(21L));
        assertThat(cache.published(List::of)).containsExactly(plan(21L));
        assertThat(loads).hasValue(2);
    }

    @Test
    void lateFillAfterInvalidationCannotResurrectOldData() {
        doAnswer(i -> {
            cache.evictAfterCommit();
            data.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), eq(ttl));
        cache.published(() -> List.of(plan(20L)));
        assertThat(cache.published(() -> List.of(plan(21L)))).containsExactly(plan(21L));
    }

    @Test
    void missingGenerationDoesNotReuseAnOldNamespace() {
        warm();
        data.remove(PlanConfigurationCache.GENERATION_KEY);
        assertThat(cache.published(() -> List.of(plan(21L)))).containsExactly(plan(21L));
    }

    @Test
    void redisOutageFallsBackToDatabase() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis down"));
        assertThat(cache.version(20L, () -> plan(20L))).isEqualTo(plan(20L));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(transactions).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
    }

    @Test
    void contendingCallerCanFinishWithoutWaitingOrPublishingItsTransactionSnapshot() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var filler = executor.submit(() -> cache.version(20L, () -> {
                loading.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("load not released");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return plan(20L);
            }));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                var contender = executor.submit(() -> cache.version(20L, () -> plan(19L)));
                assertThat(contender.get(2, TimeUnit.SECONDS)).isEqualTo(plan(19L));
                verify(transactions, times(2)).getTransaction(argThat(def ->
                        def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
            } finally {
                release.countDown();
            }
            assertThat(filler.get(5, TimeUnit.SECONDS)).isEqualTo(plan(20L));
        }
        assertThat(cache.version(20L, () -> null)).isEqualTo(plan(20L));
    }

    @Test
    void corruptJsonIsReplacedByDatabaseResult() {
        warm();
        data.replaceAll((key, value) -> key.endsWith(":published") ? "broken json" : value);
        assertThat(cache.published(() -> List.of(plan(21L)))).containsExactly(plan(21L));
        assertThat(cache.published(List::of)).containsExactly(plan(21L));
    }

    @Test
    void fillAndInvalidationFailuresDoNotFailBusinessOperation() {
        doThrow(new IllegalStateException("Redis write down"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        assertThat(cache.version(20L, () -> plan(20L))).isEqualTo(plan(20L));
        doThrow(new IllegalStateException("Redis write down")).when(values).set(anyString(), anyString());
        assertThatCode(cache::evictAfterCommit).doesNotThrowAnyException();
    }

    @Test
    void databaseFailuresAreNotCachedOrHidden() {
        assertThatThrownBy(() -> cache.version(20L, () -> { throw new IllegalArgumentException("not published"); }))
                .hasMessage("not published");
        assertThat(cache.version(20L, () -> plan(20L))).isEqualTo(plan(20L));
        verify(transactions).rollback(any());
    }

    private void warm() {
        cache.published(() -> List.of(plan(20L)));
        cache.version(20L, () -> plan(20L));
        cache.modelAccess(20L, () -> new ModelAccess("SELECTED", List.of("model-a")));
    }

    private void beginCallerTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void endCallerTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private PlanResponse plan(Long versionId) {
        return new PlanResponse(1L, "pro", "Pro", "description", versionId, versionId.intValue(),
                9900L, "CNY", new BigDecimal("123.456789"), "SELECTED", List.of("model-a"));
    }
}
