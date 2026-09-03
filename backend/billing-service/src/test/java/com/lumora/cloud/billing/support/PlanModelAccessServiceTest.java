package com.lumora.cloud.billing.support;

import com.lumora.cloud.billing.error.ApiException;
import com.lumora.cloud.billing.domain.entity.plan.PlanVersionEntity;
import com.lumora.cloud.billing.mapper.plan.PlanVersionMapper;
import com.lumora.cloud.billing.mapper.plan.PlanVersionModelMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanModelAccessServiceTest {

    private final PlanVersionMapper versionMapper = mock(PlanVersionMapper.class);
    private final PlanVersionModelMapper modelMapper = mock(PlanVersionModelMapper.class);
    private final PlanModelAccessService service = new PlanModelAccessService(versionMapper, modelMapper);

    @Test
    void letsLegacySubscriptionsUseAnyPublishedModelWithoutAJoinQuery() {
        PlanVersionEntity version = mock(PlanVersionEntity.class);
        when(version.getModelAccessMode()).thenReturn("ALL_PUBLISHED_LEGACY");
        when(versionMapper.findPublishedById(10L)).thenReturn(version);

        service.requireAllowed(10L, "model-a");

        verify(modelMapper, never()).containsModel(10L, "model-a");
    }

    @Test
    void permitsOnlyModelsCapturedByThePurchasedPlanVersion() {
        PlanVersionEntity version = mock(PlanVersionEntity.class);
        when(version.getModelAccessMode()).thenReturn("SELECTED");
        when(versionMapper.findPublishedById(20L)).thenReturn(version);
        when(modelMapper.containsModel(20L, "model-a")).thenReturn(true);

        service.requireAllowed(20L, "model-a");

        verify(modelMapper).containsModel(20L, "model-a");
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
}
