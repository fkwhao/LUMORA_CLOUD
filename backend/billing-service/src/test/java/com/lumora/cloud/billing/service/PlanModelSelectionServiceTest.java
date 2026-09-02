package com.lumora.cloud.billing.service;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.PublishedModelReference;
import com.lumora.cloud.billing.error.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanModelSelectionServiceTest {

    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final PlanModelSelectionService service = new PlanModelSelectionService(catalogClient);

    @Test
    void normalizesDeduplicatesAndKeepsSelectionOrder() {
        when(catalogClient.publishedModelReferences()).thenReturn(List.of(
                new PublishedModelReference("model-a", "Model A"),
                new PublishedModelReference("model-b", "Model B")
        ));

        assertThat(service.normalizeAndValidate(List.of(" Model-B ", "model-a", "MODEL-B")))
                .containsExactly("model-b", "model-a");
    }

    @Test
    void rejectsAnEmptySelectionBeforeCallingCatalog() {
        assertThatThrownBy(() -> service.normalizeAndValidate(List.of(" ")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("PLAN_MODELS_REQUIRED");
    }

    @Test
    void rejectsModelsThatAreNotCurrentlyPublished() {
        when(catalogClient.publishedModelReferences()).thenReturn(List.of(
                new PublishedModelReference("model-a", "Model A")
        ));

        assertThatThrownBy(() -> service.normalizeAndValidate(List.of("model-a", "model-offline")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("PLAN_MODEL_NOT_PUBLISHED");
    }

    @Test
    void reportsCatalogOutagesAsARecoverableAdministrationFailure() {
        when(catalogClient.publishedModelReferences()).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> service.normalizeAndValidate(List.of("model-a")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("MODEL_CATALOG_UNAVAILABLE");
    }
}
