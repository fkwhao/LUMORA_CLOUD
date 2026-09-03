package com.lumora.cloud.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SentinelBlockResponseTest {

    @Test
    void mapsFlowLimitToTooManyRequests() {
        SentinelBlockResponse response = SentinelBlockResponse.from(mock(FlowException.class));

        assertThat(response.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.code()).isEqualTo("REQUEST_RATE_LIMITED");
    }

    @Test
    void mapsCircuitBreakerToServiceUnavailable() {
        SentinelBlockResponse response = SentinelBlockResponse.from(mock(DegradeException.class));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.code()).isEqualTo("SERVICE_CIRCUIT_OPEN");
    }

    @Test
    void mapsSystemProtectionToServiceUnavailable() {
        SentinelBlockResponse response = SentinelBlockResponse.from(mock(SystemBlockException.class));

        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.code()).isEqualTo("SERVICE_OVERLOADED");
    }

    @Test
    void replacesUnsafeTraceIdBeforeWritingJsonOrHeaders() {
        assertThat(SentinelBlockResponse.traceId("request-1234")).isEqualTo("request-1234");
        assertThat(SentinelBlockResponse.traceId("bad\"\r\ntrace"))
                .matches("[0-9a-f-]{36}");
    }
}
