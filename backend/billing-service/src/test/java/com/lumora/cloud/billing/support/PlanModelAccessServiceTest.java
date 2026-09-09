package com.lumora.cloud.billing.support;

import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.cache.PlanConfigurationCache;
import com.lumora.cloud.billing.cache.PlanConfigurationCache.ModelAccess;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.function.Supplier;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionModelMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class PlanModelAccessServiceTest {

    private final PlanVersionMapper versionMapper = mock(PlanVersionMapper.class);
    private final PlanVersionModelMapper modelMapper = mock(PlanVersionModelMapper.class);
    private final PlanConfigurationCache cache = mock(PlanConfigurationCache.class);
    private final PlanModelAccessService service = new PlanModelAccessService(versionMapper, modelMapper, cache);

    @BeforeEach
    void loadThroughCache() {
        when(cache.modelAccess(anyLong(), any())).thenAnswer(i -> i.<Supplier<ModelAccess>>getArgument(1).get());
    }

    @Test
    void letsLegacySubscriptionsUseAnyPublishedModelWithoutAJoinQuery() {
        PlanVersionEntity version = mock(PlanVersionEntity.class);
        when(version.getModelAccessMode()).thenReturn("ALL_PUBLISHED_LEGACY");
        when(versionMapper.findPublishedById(10L)).thenReturn(version);

        service.requireAllowed(10L, "model-a");

        verify(modelMapper, never()).findModelCodes(10L);
    }

    @Test
    void permitsOnlyModelsCapturedByThePurchasedPlanVersion() {
        PlanVersionEntity version = mock(PlanVersionEntity.class);
        when(version.getModelAccessMode()).thenReturn("SELECTED");
        when(versionMapper.findPublishedById(20L)).thenReturn(version);
        when(modelMapper.findModelCodes(20L)).thenReturn(List.of("model-a"));

        service.requireAllowed(20L, "model-a");

        verify(modelMapper).findModelCodes(20L);
    }

    @Test
    void rejectsModelsOutsideThePurchasedPlanVersion() {
        PlanVersionEntity version = mock(PlanVersionEntity.class);
        when(version.getModelAccessMode()).thenReturn("SELECTED");
        when(versionMapper.findPublishedById(20L)).thenReturn(version);

        assertThatThrownBy(() -> service.requireAllowed(20L, "model-b"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("MODEL_NOT_INCLUDED_IN_PLAN");
    }

    @Test
    void rejectsSubscriptionsWhosePublishedVersionNoLongerExists() {
        when(versionMapper.findPublishedById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireAllowed(99L, "model-a"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("SUBSCRIPTION_PLAN_VERSION_INVALID");
    }

    @Test
    void usesCachedPermissionsWithoutDatabaseQueries() {
        doReturn(new ModelAccess("SELECTED", List.of("model-a")))
                .when(cache).modelAccess(org.mockito.ArgumentMatchers.eq(20L), any());
        service.requireAllowed(20L, "model-a");
        assertThatThrownBy(() -> service.requireAllowed(20L, "model-b"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("MODEL_NOT_INCLUDED_IN_PLAN");
        verifyNoInteractions(versionMapper, modelMapper);
    }
}
