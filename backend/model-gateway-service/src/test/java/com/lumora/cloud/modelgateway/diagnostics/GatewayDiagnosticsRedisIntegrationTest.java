package com.lumora.cloud.modelgateway.diagnostics;

import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.data.redis.database=15",
                "lumora.model-gateway.diagnostics.recent-limit=3"
        }
)
@EnabledIfEnvironmentVariable(named = "LUMORA_RUN_MODEL_GATEWAY_TESTS", matches = "true")
class GatewayDiagnosticsRedisIntegrationTest {

    private static final String DIAGNOSTIC_KEYS = "lumora:model-gateway:diagnostics:*";
    private static final String RECENT = "lumora:model-gateway:diagnostics:recent";
    private static final String LEGACY_INDEX = "lumora:model-gateway:diagnostics:index";
    private static final String LEGACY_RECORD_PREFIX = "lumora:model-gateway:diagnostics:record:";

    @Autowired
    private GatewayDiagnosticsStore diagnostics;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    private final String traceId = "it-trace-" + UUID.randomUUID();

    @BeforeEach
    void prepare() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<String> keys = redis.scan(ScanOptions.scanOptions().match(DIAGNOSTIC_KEYS).count(500).build())
                .collectList()
                .block();
        if (keys != null && !keys.isEmpty()) {
            redis.unlink(keys.toArray(String[]::new)).block();
        }
    }

    @Test
    void storesAndCompletesSanitizedDiagnosticInRedis() {
        GatewayRequestContext context = new GatewayRequestContext(
                42L, "session", "device", "DESKTOP", traceId, "it-client-" + UUID.randomUUID()
        );

        GatewayDiagnosticRecord started = diagnostics.started(
                context, "coding-model", "OPENAI_COMPATIBLE", true
        ).block();
        assertThat(started).isNotNull();
        assertThat(diagnostics.recent(100).collectList().block()).isEmpty();

        diagnostics.succeeded(started, "provider-a", 200).block();

        GatewayDiagnosticRecord record = diagnostics.recent(200)
                .filter(candidate -> traceId.equals(candidate.traceId()))
                .single()
                .block();
        assertThat(record).isNotNull();
        assertThat(record.status()).isEqualTo("SUCCEEDED");
        assertThat(record.modelCode()).isEqualTo("coding-model");
        assertThat(record.providerCode()).isEqualTo("provider-a");
        assertThat(record.upstreamStatus()).isEqualTo(200);
        assertThat(redis.opsForList().size(RECENT).block()).isEqualTo(1L);
        Duration recentTtl = redis.getExpire(RECENT).block();
        assertThat(recentTtl).isNotNull();
        assertThat(recentTtl.toSeconds()).isBetween(1L, Duration.ofHours(24).toSeconds());
        assertThat(redis.hasKey(LEGACY_INDEX).block()).isFalse();
        assertThat(redis.hasKey(LEGACY_RECORD_PREFIX + traceId).block()).isFalse();
    }

    @Test
    void keepsOnlyTheConfiguredNumberOfCompletedRecords() {
        List<String> traces = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            String currentTrace = "it-bounded-" + index + "-" + UUID.randomUUID();
            traces.add(currentTrace);
            GatewayRequestContext context = new GatewayRequestContext(
                    42L, "session", "device", "DESKTOP", currentTrace, "it-client-" + UUID.randomUUID()
            );
            GatewayDiagnosticRecord started = diagnostics.started(
                    context, "coding-model", "OPENAI_COMPATIBLE", true
            ).block();
            diagnostics.succeeded(started, "provider-a", 200).block();
        }

        List<GatewayDiagnosticRecord> records = diagnostics.recent(100).collectList().block();
        assertThat(records)
                .extracting(GatewayDiagnosticRecord::traceId)
                .containsExactly(traces.get(3), traces.get(2), traces.get(1));
        assertThat(redis.opsForList().size(RECENT).block()).isEqualTo(3L);
    }
}
