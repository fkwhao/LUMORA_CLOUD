package com.lumora.cloud.modelgateway.diagnostics;

import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_MODEL_GATEWAY_TESTS", matches = "true")
class GatewayDiagnosticsRedisIntegrationTest {

    private static final String INDEX = "lumora:model-gateway:diagnostics:index";
    private static final String RECORD_PREFIX = "lumora:model-gateway:diagnostics:record:";

    @Autowired
    private GatewayDiagnosticsStore diagnostics;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    private final String traceId = "it-trace-" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        redis.delete(RECORD_PREFIX + traceId).then(redis.opsForZSet().remove(INDEX, traceId)).block();
    }

    @Test
    void storesAndCompletesSanitizedDiagnosticInRedis() {
        GatewayRequestContext context = new GatewayRequestContext(
                42L, "session", "device", "DESKTOP", traceId, "it-client-" + UUID.randomUUID()
        );

        diagnostics.started(context, "coding-model", "OPENAI_COMPATIBLE", true).block();
        diagnostics.succeeded(traceId, "provider-a", 200).block();

        GatewayDiagnosticRecord record = diagnostics.recent(200)
                .filter(candidate -> traceId.equals(candidate.traceId()))
                .single()
                .block();
        assertThat(record).isNotNull();
        assertThat(record.status()).isEqualTo("SUCCEEDED");
        assertThat(record.modelCode()).isEqualTo("coding-model");
        assertThat(record.providerCode()).isEqualTo("provider-a");
        assertThat(record.upstreamStatus()).isEqualTo(200);
    }
}
