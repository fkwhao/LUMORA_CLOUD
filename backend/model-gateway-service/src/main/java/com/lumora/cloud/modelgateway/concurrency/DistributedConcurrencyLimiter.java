package com.lumora.cloud.modelgateway.concurrency;

import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DistributedConcurrencyLimiter {

    private static final String USER_PREFIX = "lumora:model-gateway:concurrency:user:";
    private static final String MODEL_PREFIX = "lumora:model-gateway:concurrency:model:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            return redis.call('ZREM', KEYS[1], ARGV[1])
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
        String leaseId = UUID.randomUUID().toString();
        String userKey = USER_PREFIX + userId;
        String modelKey = MODEL_PREFIX + modelCode;
        ConcurrencyPermit permit = new ConcurrencyPermit(userKey, modelKey, leaseId);
        return acquireKey(userKey, leaseId, properties.concurrency().perUser())
                .flatMap(userAcquired -> {
                    if (!userAcquired) {
                        return Mono.error(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                                "USER_CONCURRENCY_LIMIT", "当前账号的并发模型调用已达到上限"));
                    }
                    return acquireKey(modelKey, leaseId, properties.concurrency().perModel())
                            .onErrorResume(error -> releaseKey(userKey, leaseId)
                                    .onErrorResume(releaseError -> Mono.empty())
                                    .then(Mono.error(error)))
                            .flatMap(modelAcquired -> modelAcquired
                                    ? Mono.just(permit)
                                    : releaseKey(userKey, leaseId).then(Mono.error(new ApiException(
                                    HttpStatus.TOO_MANY_REQUESTS, "MODEL_CONCURRENCY_LIMIT",
                                    "当前模型调用繁忙，请稍后重试"))));
                })
                .onErrorMap(throwable -> throwable instanceof ApiException ? throwable
                        : new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONCURRENCY_STATE_UNAVAILABLE",
                        "模型并发控制暂时不可用", throwable));
    }

    public Mono<Void> release(ConcurrencyPermit permit) {
        return Mono.whenDelayError(
                        releaseKey(permit.userKey(), permit.leaseId()),
                        releaseKey(permit.modelKey(), permit.leaseId())
                )
                .onErrorResume(throwable -> Mono.empty());
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
        return redis.execute(RELEASE_SCRIPT, List.of(key), leaseId).next().then();
    }
}
