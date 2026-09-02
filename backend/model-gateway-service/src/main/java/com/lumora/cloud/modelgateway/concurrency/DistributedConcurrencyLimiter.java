package com.lumora.cloud.modelgateway.concurrency;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class DistributedConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedConcurrencyLimiter.class);
    private static final String USER_PREFIX = "lumora:model-gateway:concurrency:user:";
    private static final String MODEL_PREFIX = "lumora:model-gateway:concurrency:model:";
    private static final String ACCOUNT_PREFIX = "lumora:model-gateway:concurrency:account:";
    private static final String ROUTE_PREFIX = "lumora:model-gateway:concurrency:route:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 1
            """, Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final ModelGatewayProperties properties;

    public DistributedConcurrencyLimiter(
            ReactiveStringRedisTemplate redis,
            ModelGatewayProperties properties
    ) {
        this.redis = redis;
        this.properties = properties;
    }

    public Mono<ConcurrencyPermit> acquire(long userId, String modelCode) {
        return acquire(userId, modelCode, null);
    }

    public Mono<ConcurrencyPermit> acquire(long userId, String modelCode, ResolvedModelRoute route) {
        String leaseId = UUID.randomUUID().toString();
        List<KeyLimit> limits = new ArrayList<>();
        limits.add(new KeyLimit(USER_PREFIX + userId, properties.concurrency().perUser(),
                "USER_CONCURRENCY_LIMIT", "当前账号的并发模型调用已达到上限", false));
        if (properties.concurrency().perModel() > 0) {
            limits.add(new KeyLimit(MODEL_PREFIX + modelCode, properties.concurrency().perModel(),
                    "MODEL_CONCURRENCY_LIMIT", "当前逻辑模型调用繁忙，请稍后重试", false));
        }
        if (route != null && route.accountMaxConcurrency() != null) {
            limits.add(new KeyLimit(ACCOUNT_PREFIX + route.providerId(), route.accountMaxConcurrency(),
                    "PROVIDER_ACCOUNT_CONCURRENCY_LIMIT", "供应商账号并发已满", true));
        }
        if (route != null && route.maxConcurrency() != null) {
            limits.add(new KeyLimit(ROUTE_PREFIX + route.routeId(), route.maxConcurrency(),
                    "MODEL_ROUTE_CONCURRENCY_LIMIT", "上游路由并发已满", true));
        }
        List<String> acquired = new ArrayList<>(limits.size());
        return acquireNext(limits, 0, acquired, leaseId)
                .then(Mono.fromSupplier(() -> new ConcurrencyPermit(acquired, leaseId)))
                .onErrorMap(throwable -> throwable instanceof ApiException ? throwable
                        : new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONCURRENCY_STATE_UNAVAILABLE",
                        "模型并发控制暂时不可用", throwable));
    }

    public Mono<Void> release(ConcurrencyPermit permit) {
        return releaseKeys(permit.keys(), permit.leaseId())
                .onErrorResume(throwable -> {
                    log.warn("Failed to release model concurrency lease leaseId={} keys={}",
                            permit.leaseId(), permit.keys(), throwable);
                    return Mono.empty();
                });
    }

    private Mono<Void> acquireNext(List<KeyLimit> limits, int index, List<String> acquired, String leaseId) {
        if (index >= limits.size()) {
            return Mono.empty();
        }
        KeyLimit current = limits.get(index);
        return acquireKey(current.key(), leaseId, current.limit())
                .flatMap(success -> {
                    if (!success) {
                        RuntimeException failure = current.routeScoped()
                                ? new RouteCapacityException(current.code(), current.message())
                                : new ApiException(HttpStatus.TOO_MANY_REQUESTS, current.code(), current.message());
                        return releaseKeys(acquired, leaseId).then(Mono.error(failure));
                    }
                    acquired.add(current.key());
                    return acquireNext(limits, index + 1, acquired, leaseId);
                })
                .onErrorResume(error -> releaseKeys(acquired, leaseId)
                        .onErrorResume(releaseError -> Mono.empty())
                        .then(Mono.error(error)));
    }

    private Mono<Void> releaseKeys(List<String> keys, String leaseId) {
        return keys.isEmpty()
                ? Mono.empty()
                : Flux.fromIterable(keys)
                        .concatMap(key -> releaseKey(key, leaseId))
                        .then();
    }

    private Mono<Boolean> acquireKey(String key, String leaseId, int limit) {
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + properties.concurrency().leaseTtl().toMillis();
        long keyTtl = properties.concurrency().leaseTtl().multipliedBy(2).toMillis();
        return redis.execute(ACQUIRE_SCRIPT, List.of(key),
                        Long.toString(now), Long.toString(expiresAt), Integer.toString(limit), leaseId,
                        Long.toString(keyTtl))
                .next()
                .map(result -> result != null && result == 1L)
                .switchIfEmpty(Mono.error(new IllegalStateException("Redis returned no concurrency result")));
    }

    private Mono<Void> releaseKey(String key, String leaseId) {
        return redis.opsForZSet().remove(key, leaseId).then();
    }

    private record KeyLimit(String key, int limit, String code, String message, boolean routeScoped) {
    }
}
