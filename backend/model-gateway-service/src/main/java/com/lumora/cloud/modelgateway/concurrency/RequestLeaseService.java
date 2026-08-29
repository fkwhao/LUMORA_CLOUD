package com.lumora.cloud.modelgateway.concurrency;

import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.service.RequestIds;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class RequestLeaseService {

    private static final String PREFIX = "lumora:model-gateway:request:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final ModelGatewayProperties properties;
    private final RequestIds requestIds;

    public RequestLeaseService(
            ReactiveStringRedisTemplate redis,
            ModelGatewayProperties properties,
            RequestIds requestIds
    ) {
        this.redis = redis;
        this.properties = properties;
        this.requestIds = requestIds;
    }

    public Mono<RequestLease> acquire(GatewayRequestContext context) {
        String billingRequestId = requestIds.billingRequestId(context.userId(), context.clientRequestId());
        String key = PREFIX + requestIds.keyDigest(context.userId(), context.clientRequestId());
        String token = UUID.randomUUID().toString();
        RequestLease lease = new RequestLease(key, token, billingRequestId);
        return redis.opsForValue().setIfAbsent(key, token, properties.concurrency().requestLeaseTtl())
                .flatMap(acquired -> Boolean.TRUE.equals(acquired)
                        ? Mono.just(lease)
                        : Mono.error(new ApiException(HttpStatus.CONFLICT, "MODEL_REQUEST_IN_PROGRESS",
                        "相同客户端请求正在处理，请勿重复提交")))
                .switchIfEmpty(Mono.error(new IllegalStateException("Redis returned no request lease result")))
                .onErrorMap(throwable -> throwable instanceof ApiException ? throwable
                        : new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONCURRENCY_STATE_UNAVAILABLE",
                        "模型请求并发状态暂时不可用", throwable));
    }

    public Mono<Void> release(RequestLease lease) {
        return redis.execute(RELEASE_SCRIPT, List.of(lease.key()), lease.token())
                .next()
                .then()
                .onErrorResume(throwable -> Mono.empty());
    }
}
