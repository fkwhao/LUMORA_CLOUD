package com.lumora.cloud.billing.service.impl;

import com.lumora.cloud.billing.cache.PlanConfigurationCache;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanRequest;
import com.lumora.cloud.billing.domain.dto.plan.CreatePlanVersionRequest;
import com.lumora.cloud.billing.domain.entity.plan.BillingPlanEntity;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.mapper.plan.BillingPlanMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionModelMapper;
import com.lumora.cloud.billing.support.PlanModelSelectionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillingCatalogCacheTest {
    private final BillingPlanMapper plans = mock(BillingPlanMapper.class);
    private final PlanVersionMapper versions = mock(PlanVersionMapper.class);
    private final PlanVersionModelMapper models = mock(PlanVersionModelMapper.class);
    private final PlanModelSelectionService selection = mock(PlanModelSelectionService.class);
    private final PlanConfigurationCache cache = mock(PlanConfigurationCache.class);
    private final BillingCatalogServiceImpl service = new BillingCatalogServiceImpl(plans, versions, models, selection, cache);
    private final PlanResponse plan = new PlanResponse(1L, "pro", "Pro", null, 20L, 1,
            9900L, "CNY", new BigDecimal("10.000000"), "SELECTED", List.of("model-a"));

    @Test
    void cachedPlanListStillTracksModelAvailability() {
        when(cache.published(any())).thenReturn(List.of(plan));
        when(selection.availableModelCodes()).thenReturn(Set.of("model-a"), Set.of(), Set.of("model-a"));
        assertThat(service.listPublished()).containsExactly(plan);
        assertThat(service.listPublished()).isEmpty();
        assertThat(service.listPublished()).containsExactly(plan);
        verifyNoInteractions(plans, versions, models);
    }

    @Test
    void cachedVersionDoesNotBypassPurchaseAvailabilityCheck() {
        when(cache.version(eq(20L), any())).thenReturn(plan);
        when(selection.availableModelCodes()).thenReturn(Set.of(), Set.of("model-a"));
        assertThatThrownBy(() -> service.purchasableVersion(20L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode()).isEqualTo("PLAN_HAS_NO_AVAILABLE_MODELS");
        assertThat(service.purchasableVersion(20L)).isEqualTo(plan);
        verifyNoInteractions(plans, versions, models);
    }

    @Test
    void creatingPlanSchedulesInvalidationAfterWritingVersionAndModels() {
        prepareInsert();
        when(plans.insert(any(BillingPlanEntity.class))).thenAnswer(i -> {
            ReflectionTestUtils.setField(i.<BillingPlanEntity>getArgument(0), "id", 1L);
            return 1;
        });
        assertThat(service.create(new CreatePlanRequest("pro", "Pro", null, 9900L, "CNY",
                BigDecimal.TEN, List.of("model-a")))).isEqualTo(plan);
        var order = inOrder(versions, models, cache);
        order.verify(versions).insert(any(PlanVersionEntity.class));
        order.verify(models).insert(any(com.lumora.cloud.billing.domain.entity.plan.PlanVersionModelEntity.class));
        order.verify(cache).evictAfterCommit();
    }

    @Test
    void publishingNewVersionSchedulesInvalidation() {
        prepareInsert();
        var entity = BillingPlanEntity.create("pro", "Pro", null);
        ReflectionTestUtils.setField(entity, "id", 1L);
        when(plans.findByIdForUpdate(1L)).thenReturn(entity);
        when(versions.maxVersionNo(1L)).thenReturn(1);
        var published = service.publishVersion(1L, new CreatePlanVersionRequest(9900L, "CNY",
                BigDecimal.TEN, List.of("model-a")));
        assertThat(published.versionNo()).isEqualTo(2);
        verify(cache).evictAfterCommit();
    }

    @Test
    void failedPublishDoesNotScheduleInvalidation() {
        when(selection.normalizeAndValidate(any())).thenReturn(List.of("model-a"));
        var entity = BillingPlanEntity.create("pro", "Pro", null);
        ReflectionTestUtils.setField(entity, "id", 1L);
        when(plans.findByIdForUpdate(1L)).thenReturn(entity);
        when(versions.insert(any(PlanVersionEntity.class))).thenThrow(new DuplicateKeyException("version conflict"));
        assertThatThrownBy(() -> service.publishVersion(1L, new CreatePlanVersionRequest(9900L, "CNY",
                BigDecimal.TEN, List.of("model-a")))).isInstanceOf(ApiException.class);
        verifyNoInteractions(cache);
    }

    private void prepareInsert() {
        when(selection.normalizeAndValidate(any())).thenReturn(List.of("model-a"));
        when(versions.insert(any(PlanVersionEntity.class))).thenAnswer(i -> {
            ReflectionTestUtils.setField(i.<PlanVersionEntity>getArgument(0), "id", 20L);
            return 1;
        });
        when(models.findModelCodes(20L)).thenReturn(List.of("model-a"));
    }
}
