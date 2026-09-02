package com.lumora.cloud.modelgateway.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class GatewayDiagnosticsStore {

    private static final Logger log = LoggerFactory.getLogger(GatewayDiagnosticsStore.class);
    private static final String RECORD_PREFIX = "lumora:model-gateway:diagnostics:record:";
    private static final String INDEX = "lumora:model-gateway:diagnostics:index";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final GatewayDiagnosticsProperties properties;

    public GatewayDiagnosticsStore(
            ReactiveStringRedisTemplate redis,
            ObjectMapper objectMapper,
            GatewayDiagnosticsProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Mono<Void> started(
            GatewayRequestContext context, String modelCode, String protocol, boolean stream
    ) {
        Instant now = Instant.now();
        GatewayDiagnosticRecord record = new GatewayDiagnosticRecord(
                context.traceId(), context.clientRequestId(), context.userId(), modelCode,
                "", "", "", protocol, stream, "RUNNING", null, null, 0L, now, null
        );
        return write(record)
                .then(redis.opsForZSet().add(INDEX, record.traceId(), now.toEpochMilli()))
                .then(prune(now))
                .onErrorResume(error -> ignoredWriteFailure("start", context.traceId(), error));
    }

    public Mono<Void> succeeded(String traceId, String providerCode, String routeId, String routeName, int upstreamStatus) {
        return complete(traceId, providerCode, routeId, routeName, "SUCCEEDED", upstreamStatus, null);
    }

    public Mono<Void> succeeded(String traceId, String providerCode, int upstreamStatus) {
        return succeeded(traceId, providerCode, "", "", upstreamStatus);
    }

    public Mono<Void> failed(String traceId, String providerCode, String routeId, String routeName, Integer upstreamStatus, String errorCode) {
        return complete(traceId, providerCode, routeId, routeName, "FAILED", upstreamStatus, errorCode);
    }

    public Mono<Void> failed(String traceId, String providerCode, Integer upstreamStatus, String errorCode) {
        return failed(traceId, providerCode, "", "", upstreamStatus, errorCode);
    }

    public Mono<Void> canceled(String traceId, String providerCode, String routeId, String routeName) {
        return complete(traceId, providerCode, routeId, routeName, "CANCELED", null, "CLIENT_CANCELED");
    }

    public Mono<Void> canceled(String traceId, String providerCode) {
        return canceled(traceId, providerCode, "", "");
    }

    public Flux<GatewayDiagnosticRecord> recent(int limit) {
        int bounded = Math.max(1, Math.min(limit, 200));
        return redis.opsForZSet().reverseRange(INDEX, Range.closed(0L, bounded - 1L))
                .flatMapSequential(this::load);
    }

    public Mono<List<GatewayDiagnosticRecord>> summaryWindow() {
        Instant startsAt = Instant.now().minus(properties.getSummaryWindow());
        return redis.opsForZSet()
                .reverseRangeByScore(INDEX, Range.closed((double) startsAt.toEpochMilli(), Double.MAX_VALUE))
                .take(properties.getMaxRecords())
                .flatMapSequential(this::load)
                .collectList();
    }

    private Mono<Void> complete(
            String traceId, String providerCode, String routeId, String routeName,
            String status, Integer upstreamStatus, String errorCode
    ) {
        return load(traceId)
                .flatMap(record -> write(record.withCompletion(
                        providerCode, routeId, routeName, status, upstreamStatus, errorCode, Instant.now()
                )))
                .onErrorResume(error -> ignoredWriteFailure("complete", traceId, error));
    }

    private Mono<Void> write(GatewayDiagnosticRecord record) {
        try {
            return redis.opsForValue()
                    .set(key(record.traceId()), objectMapper.writeValueAsString(record), properties.getRetention())
                    .then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    private Mono<GatewayDiagnosticRecord> load(String traceId) {
        return redis.opsForValue().get(key(traceId))
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, GatewayDiagnosticRecord.class));
                    } catch (JsonProcessingException exception) {
                        return redis.opsForZSet().remove(INDEX, traceId).then(Mono.empty());
                    }
                })
                .switchIfEmpty(redis.opsForZSet().remove(INDEX, traceId).then(Mono.empty()));
    }

    private Mono<Void> prune(Instant now) {
        double oldest = now.minus(properties.getRetention()).toEpochMilli();
        return redis.opsForZSet().removeRangeByScore(INDEX, Range.closed(0D, oldest))
                .then(redis.opsForZSet().size(INDEX))
                .flatMap(size -> size != null && size > properties.getMaxRecords()
                        ? redis.opsForZSet().removeRange(
                                INDEX, Range.closed(0L, size - properties.getMaxRecords() - 1L)
                        ).then()
                        : Mono.empty())
                .then();
    }

    private Mono<Void> ignoredWriteFailure(String operation, String traceId, Throwable error) {
        log.warn("Could not {} gateway diagnostic {}; model request remains available",
                operation, traceId, error);
        return Mono.empty();
    }

    private String key(String traceId) {
        return RECORD_PREFIX + traceId;
    }
}
