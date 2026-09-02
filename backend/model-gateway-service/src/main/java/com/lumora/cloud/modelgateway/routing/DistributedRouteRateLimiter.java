package com.lumora.cloud.modelgateway.routing;

import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.modelgateway.concurrency.RouteCapacityException;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DistributedRouteRateLimiter {

    private static final String PREFIX = "lumora:model-gateway:rate:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            for index = 1, #KEYS do
                local limit = tonumber(ARGV[(index - 1) * 2 + 1])
                local increment = tonumber(ARGV[(index - 1) * 2 + 2])
                local current = tonumber(redis.call('GET', KEYS[index]) or '0')
                if current + increment > limit then
                    return index
                end
            end
            for index = 1, #KEYS do
                local increment = tonumber(ARGV[(index - 1) * 2 + 2])
                redis.call('INCRBY', KEYS[index], increment)
                redis.call('PEXPIRE', KEYS[index], ARGV[#KEYS * 2 + 1])
            end
            return 0
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public DistributedRouteRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> acquire(ResolvedModelRoute route, long estimatedTokens) {
        long minute = Instant.now().getEpochSecond() / 60;
        List<String> keys = new ArrayList<>(4);
        List<String> arguments = new ArrayList<>(9);
        if (route.providerId() != null && route.accountRequestsPerMinute() != null) {
            add(keys, arguments, "account:rpm:" + route.providerId() + ":" + minute,
                    route.accountRequestsPerMinute(), 1);
        }
        if (route.requestsPerMinute() != null) {
            add(keys, arguments, "route:rpm:" + route.routeId() + ":" + minute,
                    route.requestsPerMinute(), 1);
        }
        if (route.providerId() != null && route.accountTokensPerMinute() != null) {
            add(keys, arguments, "account:tpm:" + route.providerId() + ":" + minute,
                    route.accountTokensPerMinute(), estimatedTokens);
        }
        if (route.tokensPerMinute() != null) {
            add(keys, arguments, "route:tpm:" + route.routeId() + ":" + minute,
                    route.tokensPerMinute(), estimatedTokens);
        }
        if (keys.isEmpty()) {
            return Mono.empty();
        }
        arguments.add("120000");
        return redis.execute(ACQUIRE_SCRIPT, keys, arguments.toArray())
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("Redis returned no rate-limit result")))
                .flatMap(blockedIndex -> blockedIndex == 0L
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new RouteCapacityException(
                        "MODEL_ROUTE_RATE_LIMIT", "供应商账号或上游路由已达到 RPM/TPM 上限"
                )))
                .onErrorMap(error -> error instanceof ApiException ? error : new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_STATE_UNAVAILABLE",
                        "模型速率控制暂时不可用", error
                ));
    }

    private void add(List<String> keys, List<String> arguments, String suffix, long limit, long increment) {
        keys.add(PREFIX + suffix);
        arguments.add(Long.toString(limit));
        arguments.add(Long.toString(Math.max(1L, increment)));
    }
}
