package com.lumora.cloud.modelgateway.config;

import com.lumora.cloud.api.fallback.BillingClientFallbackFactory;
import com.lumora.cloud.api.fallback.CatalogClientFallbackFactory;
import com.lumora.cloud.api.fallback.RemoteServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeignFallbackFactoryTest {

    @Test
    void catalogFallbackNeverReturnsSyntheticConfiguration() {
        var fallback = new CatalogClientFallbackFactory().create(new Exception("circuit open"));

        assertThatThrownBy(() -> fallback.resolve("test-model"))
                .isInstanceOf(RemoteServiceUnavailableException.class)
                .hasMessageContaining("lumora-model-catalog-service#resolve");
    }

    @Test
    void billingFallbackNeverReturnsSyntheticSettlement() {
        var fallback = new BillingClientFallbackFactory().create(new Exception("circuit open"));

        assertThatThrownBy(() -> fallback.reserve(null))
                .isInstanceOf(RemoteServiceUnavailableException.class)
                .hasMessageContaining("lumora-billing-service#reserve");
    }
}
