package com.lumora.cloud.modelgateway.cache;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.fallback.RemoteServiceUnavailableException;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.DecodeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConfigCacheTest {

    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final ModelConfigCache cache = new ModelConfigCache(catalogClient, properties());

    @Test
    void mapsMissingModelToBusinessNotFound() {
        when(catalogClient.resolve("missing-model")).thenThrow(feignFailure(404, "not found"));

        assertApiError(cache.resolve("missing-model"), HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE");
    }

    @Test
    void distinguishesNoAvailableCatalogInstance() {
        when(catalogClient.resolve("test-model")).thenThrow(feignFailure(
                503, "Load balancer does not contain an instance for the service lumora-model-catalog-service"
        ));

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_NO_AVAILABLE_INSTANCE"
        );
    }

    @Test
    void distinguishesCatalogConnectionFailure() {
        RetryableException failure = new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET,
                new ConnectException("Connection refused"), (Long) null, request()
        );
        when(catalogClient.resolve("test-model")).thenThrow(failure);

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_CONNECTION_FAILED"
        );
    }

    @Test
    void distinguishesSuccessfulResponseDecodeFailure() {
        when(catalogClient.resolve("test-model")).thenThrow(new DecodeException(
                200, "Unable to decode catalog response", request()
        ));

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_DECODE_FAILED"
        );
    }

    @Test
    void distinguishesInternalAuthenticationFailure() {
        when(catalogClient.resolve("test-model")).thenThrow(feignFailure(403, "forbidden"));

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_AUTH_FAILED"
        );
    }

    @Test
    void distinguishesCatalogServerFailure() {
        when(catalogClient.resolve("test-model")).thenThrow(feignFailure(500, "server error"));

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_BAD_RESPONSE"
        );
    }

    @Test
    void mapsSentinelFallbackToProtectedCatalogError() {
        when(catalogClient.resolve("test-model")).thenThrow(new RemoteServiceUnavailableException(
                "lumora-model-catalog-service", "resolve", new Exception("blocked")
        ));

        assertApiError(
                cache.resolve("test-model"), HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_CATALOG_PROTECTED"
        );
    }

    private void assertApiError(Mono<?> result, HttpStatus expectedStatus, String expectedCode) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(expectedStatus);
                    assertThat(apiException.getCode()).isEqualTo(expectedCode);
                })
                .verify();
    }

    private FeignException feignFailure(int status, String body) {
        return FeignException.errorStatus(
                "CatalogClient#resolve",
                Response.builder()
                        .status(status)
                        .reason("test")
                        .request(request())
                        .headers(Map.of())
                        .body(body, StandardCharsets.UTF_8)
                        .build()
        );
    }

    private Request request() {
        return Request.create(
                Request.HttpMethod.GET,
                "http://lumora-model-catalog-service/internal/catalog/models/test-model",
                Map.of(), null, StandardCharsets.UTF_8, null
        );
    }

    private ModelGatewayProperties properties() {
        return new ModelGatewayProperties(
                new ModelGatewayProperties.Catalog(Duration.ofMinutes(1), 100),
                new ModelGatewayProperties.Concurrency(
                        2, 100, Duration.ofMinutes(2), Duration.ofMinutes(2)
                ),
                new ModelGatewayProperties.Provider(
                        Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofMinutes(1),
                        100, 1_000, Duration.ofSeconds(2), Duration.ofSeconds(30), 65_536, 1_048_576
                ),
                new ModelGatewayProperties.Recovery(Duration.ofHours(1), Duration.ofMinutes(1), 100)
        );
    }
}
