package com.lumora.cloud.modelgateway.concurrency;

import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.service.RequestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "lumora.model-gateway.catalog.local-cache-ttl=PT30S",
                "lumora.model-gateway.catalog.maximum-size=100",
                "lumora.model-gateway.concurrency.per-user=2",
                "lumora.model-gateway.concurrency.per-model=2",
                "lumora.model-gateway.concurrency.lease-ttl=PT1M",
                "lumora.model-gateway.concurrency.request-lease-ttl=PT1M",
                "lumora.model-gateway.provider.connect-timeout=PT2S",
                "lumora.model-gateway.provider.response-timeout=PT10S",
                "lumora.model-gateway.provider.max-call-duration=PT30S",
                "lumora.model-gateway.provider.max-connections=10",
                "lumora.model-gateway.provider.pending-acquire-max-count=10",
                "lumora.model-gateway.provider.pending-acquire-timeout=PT1S",
                "lumora.model-gateway.provider.max-idle-time=PT30S",
                "lumora.model-gateway.provider.max-error-body-bytes=65536",
                "lumora.model-gateway.provider.max-buffered-response-bytes=1048576",
                "lumora.model-gateway.recovery.retention=PT1H",
                "lumora.model-gateway.recovery.scan-interval=PT10M",
                "lumora.model-gateway.recovery.batch-size=10"
        }
)
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_MODEL_GATEWAY_TESTS", matches = "true")
class RedisConcurrencyIntegrationTest {

    @Autowired private RequestLeaseService requestLeases;
    @Autowired private DistributedConcurrencyLimiter limiter;
    @Autowired private RequestIds requestIds;
    @Autowired private ReactiveStringRedisTemplate redis;

    private final long userId = 8_000_000_000L + Math.abs(UUID.randomUUID().hashCode());
    private final String clientRequestId = "it-client-" + UUID.randomUUID();
    private final String modelCode = "it-model-" + UUID.randomUUID();

    @AfterEach
    void cleanKeys() {
        redis.delete(
                "lumora:model-gateway:request:" + requestIds.keyDigest(userId, clientRequestId),
                "lumora:model-gateway:concurrency:user:" + userId,
                "lumora:model-gateway:concurrency:model:" + modelCode
        ).block();
    }

    @Test
    void sharesIdempotencyAndConcurrencyLimitsThroughRedis() {
        GatewayRequestContext context = new GatewayRequestContext(
                userId, "session", "device", "DESKTOP", "trace", clientRequestId
        );
        RequestLease requestLease = requestLeases.acquire(context).block();
        assertThat(requestLease).isNotNull();
        StepVerifier.create(requestLeases.acquire(context))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("MODEL_REQUEST_IN_PROGRESS");
                })
                .verify();

        ConcurrencyPermit first = limiter.acquire(userId, modelCode).block();
        ConcurrencyPermit second = limiter.acquire(userId, modelCode).block();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        StepVerifier.create(limiter.acquire(userId, modelCode))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("USER_CONCURRENCY_LIMIT");
                })
                .verify();

        limiter.release(first).block();
        assertThat(limiter.acquire(userId, modelCode).block()).isNotNull();
        requestLeases.release(requestLease).block();
        assertThat(requestLeases.acquire(context).block()).isNotNull();
    }
}
