package com.lumora.cloud.modelgateway.service;

import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.fallback.RemoteServiceUnavailableException;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingControlServiceTest {

    private final BillingClient billingClient = mock(BillingClient.class);
    private final BillingControlService billing = new BillingControlService(billingClient);

    @Test
    void failsClosedWhenSentinelFallbackProtectsBilling() {
        ReserveRequest request = request();
        when(billingClient.reserve(request)).thenThrow(new RemoteServiceUnavailableException(
                "lumora-billing-service", "reserve", new Exception("blocked")
        ));

        StepVerifier.create(billing.reserve(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiException.getCode()).isEqualTo("BILLING_PROTECTED");
                })
                .verify();
    }

    private ReserveRequest request() {
        Instant now = Instant.now();
        return new ReserveRequest(
                "request-1", "client-request-1", 1L, "test-model", "pricing-v1",
                BigDecimal.ONE, now, BigDecimal.ONE, null, now.plusSeconds(60)
        );
    }
}
