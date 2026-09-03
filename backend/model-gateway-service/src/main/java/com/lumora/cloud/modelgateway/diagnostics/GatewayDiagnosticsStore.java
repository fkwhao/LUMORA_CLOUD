package com.lumora.cloud.modelgateway.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GatewayDiagnosticsStore {

    private static final Logger log = LoggerFactory.getLogger(GatewayDiagnosticsStore.class);
    private static final String RECENT = "lumora:model-gateway:diagnostics:recent";
    private static final String BUCKET_PREFIX = "lumora:model-gateway:diagnostics:bucket:";
    private static final String LEGACY_RECORD_PREFIX = "lumora:model-gateway:diagnostics:record:";
    private static final String LEGACY_INDEX = "lumora:model-gateway:diagnostics:index";
    private static final DefaultRedisScript<Long> APPEND_RECENT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('LPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], 0, tonumber(ARGV[2]) - 1)
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return redis.call('LLEN', KEYS[1])
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveHashOperations<String, String, String> buckets;
    private final ReactiveListOperations<String, String> recent;
    private final ObjectMapper objectMapper;
    private final GatewayDiagnosticsProperties properties;

    public GatewayDiagnosticsStore(
            ReactiveStringRedisTemplate redis,
            ObjectMapper objectMapper,
            GatewayDiagnosticsProperties properties
    ) {
        this.redis = redis;
        this.buckets = redis.opsForHash();
        this.recent = redis.opsForList();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Mono<GatewayDiagnosticRecord> started(
            GatewayRequestContext context, String modelCode, String protocol, boolean stream
    ) {
        Instant now = Instant.now();
        GatewayDiagnosticRecord record = new GatewayDiagnosticRecord(
                context.traceId(), context.clientRequestId(), context.userId(), modelCode,
                "", "", "", protocol, stream, "RUNNING", null, null, 0L, now, null
        );
        return recordStarted(now)
                .onErrorResume(error -> ignoredWriteFailure("start metrics", context.traceId(), error))
                .thenReturn(record);
    }

    public Mono<Void> succeeded(
            GatewayDiagnosticRecord started,
            String providerCode,
            String routeId,
            String routeName,
            int upstreamStatus
    ) {
        return complete(started, providerCode, routeId, routeName, "SUCCEEDED", upstreamStatus, null);
    }

    public Mono<Void> succeeded(GatewayDiagnosticRecord started, String providerCode, int upstreamStatus) {
        return succeeded(started, providerCode, "", "", upstreamStatus);
    }

    public Mono<Void> failed(
            GatewayDiagnosticRecord started,
            String providerCode,
            String routeId,
            String routeName,
            Integer upstreamStatus,
            String errorCode
    ) {
        return complete(started, providerCode, routeId, routeName, "FAILED", upstreamStatus, errorCode);
    }

    public Mono<Void> failed(
            GatewayDiagnosticRecord started, String providerCode, Integer upstreamStatus, String errorCode
    ) {
        return failed(started, providerCode, "", "", upstreamStatus, errorCode);
    }

    public Mono<Void> canceled(
            GatewayDiagnosticRecord started, String providerCode, String routeId, String routeName
    ) {
        return complete(started, providerCode, routeId, routeName, "CANCELED", null, "CLIENT_CANCELED");
    }

    public Mono<Void> canceled(GatewayDiagnosticRecord started, String providerCode) {
        return canceled(started, providerCode, "", "");
    }

    public Flux<GatewayDiagnosticRecord> recent(int limit) {
        int bounded = Math.max(1, Math.min(limit, properties.getRecentLimit()));
        return recent.range(RECENT, 0L, bounded - 1L)
                .flatMapSequential(this::decode);
    }

    public Mono<GatewayDiagnosticsSnapshot> summaryWindow() {
        Instant now = Instant.now();
        List<String> keys = summaryBucketKeys(now);
        int concurrency = Math.min(32, Math.max(1, keys.size()));
        return Flux.fromIterable(keys)
                .flatMap(key -> buckets.entries(key)
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .map(GatewayDiagnosticsSnapshot::from), concurrency)
                .reduce(GatewayDiagnosticsSnapshot.empty(), GatewayDiagnosticsSnapshot::plus);
    }

    private Mono<Void> complete(
            GatewayDiagnosticRecord started,
            String providerCode, String routeId, String routeName,
            String status, Integer upstreamStatus, String errorCode
    ) {
        GatewayDiagnosticRecord completed = started.withCompletion(
                providerCode, routeId, routeName, status, upstreamStatus, errorCode, Instant.now()
        );
        Mono<Void> metrics = recordCompleted(started.startedAt(), completed)
                .onErrorResume(error -> ignoredWriteFailure("complete metrics", started.traceId(), error));
        Mono<Void> detail = appendRecent(completed)
                .onErrorResume(error -> ignoredWriteFailure("append recent detail", started.traceId(), error));
        return Mono.when(metrics, detail).then();
    }

    private Mono<Void> recordStarted(Instant startedAt) {
        String bucket = bucketKey(startedAt);
        return Mono.when(
                        buckets.increment(bucket, "total", 1L),
                        buckets.increment(bucket, "running", 1L)
                )
                .then(redis.expire(bucket, properties.getBucketRetention()))
                .then();
    }

    private Mono<Void> recordCompleted(Instant startedAt, GatewayDiagnosticRecord completed) {
        String bucket = bucketKey(startedAt);
        String statusField = switch (completed.status()) {
            case "SUCCEEDED" -> "succeeded";
            case "FAILED" -> "failed";
            case "CANCELED" -> "canceled";
            default -> throw new IllegalArgumentException("Unsupported diagnostic status: " + completed.status());
        };
        return Mono.when(
                        buckets.increment(bucket, "running", -1L),
                        buckets.increment(bucket, statusField, 1L),
                        buckets.increment(bucket, "duration_count", 1L),
                        buckets.increment(bucket, "duration_total_millis", completed.durationMillis()),
                        buckets.increment(
                                bucket,
                                GatewayDiagnosticsSnapshot.histogramField(completed.durationMillis()),
                                1L
                        )
                )
                .then(redis.expire(bucket, properties.getBucketRetention()))
                .then();
    }

    private List<String> summaryBucketKeys(Instant now) {
        long bucketMillis = summaryBucketMillis();
        long first = floorBucket(now.minus(properties.getSummaryWindow()).toEpochMilli(), bucketMillis);
        long last = floorBucket(now.toEpochMilli(), bucketMillis);
        List<String> keys = new ArrayList<>((int) ((last - first) / bucketMillis) + 1);
        for (long bucket = first; bucket <= last; bucket += bucketMillis) {
            keys.add(BUCKET_PREFIX + bucket);
        }
        return keys;
    }

    private String bucketKey(Instant instant) {
        long bucketMillis = summaryBucketMillis();
        return BUCKET_PREFIX + floorBucket(instant.toEpochMilli(), bucketMillis);
    }

    private long summaryBucketMillis() {
        long millis = properties.getSummaryBucket().toMillis();
        if (millis <= 0L) {
            throw new IllegalStateException("Diagnostic summary bucket must be positive");
        }
        return millis;
    }

    private long floorBucket(long epochMillis, long bucketMillis) {
        return Math.floorDiv(epochMillis, bucketMillis) * bucketMillis;
    }

    private Mono<Void> appendRecent(GatewayDiagnosticRecord record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            return redis.execute(
                            APPEND_RECENT_SCRIPT,
                            List.of(RECENT),
                            json,
                            Integer.toString(properties.getRecentLimit()),
                            Long.toString(properties.getRecentRetention().toMillis())
                    )
                    .next()
                    .then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    private Mono<GatewayDiagnosticRecord> decode(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, GatewayDiagnosticRecord.class));
        } catch (JsonProcessingException exception) {
            log.warn("Ignoring malformed recent gateway diagnostic", exception);
            return Mono.empty();
        }
    }

    private Mono<Void> ignoredWriteFailure(String operation, String traceId, Throwable error) {
        log.warn("Could not {} gateway diagnostic {}; model request remains available",
                operation, traceId, error);
        return Mono.empty();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupLegacyStorage() {
        ScanOptions scan = ScanOptions.scanOptions()
                .match(LEGACY_RECORD_PREFIX + "*")
                .count(500)
                .build();
        redis.scan(scan)
                .buffer(500)
                .concatMap(keys -> keys.isEmpty()
                        ? Mono.empty()
                        : redis.unlink(keys.toArray(String[]::new)))
                .then(redis.delete(LEGACY_INDEX))
                .doOnNext(deleted -> {
                    if (deleted > 0L) {
                        log.info("Removed legacy gateway diagnostic index");
                    }
                })
                .doOnError(error -> log.warn("Could not remove legacy gateway diagnostic storage", error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}
