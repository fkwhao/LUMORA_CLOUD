package com.lumora.cloud.modelgateway.recovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecoveryStore {

    private static final String COMMAND_PREFIX = "lumora:model-gateway:recovery:command:";
    private static final String DUE_INDEX = "lumora:model-gateway:recovery:due";
    private static final DefaultRedisScript<Long> SCHEDULE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) <= tonumber(ARGV[2]) then
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
                return 1
            end
            return 0
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ModelGatewayProperties properties;

    public Mono<Void> schedule(RecoveryCommand command, Instant dueAt) {
        try {
            String json = objectMapper.writeValueAsString(command);
            return redis.execute(SCHEDULE_SCRIPT, List.of(key(command.requestId()), DUE_INDEX),
                            json,
                            Long.toString(properties.recovery().retention().toMillis()),
                            Long.toString(dueAt.toEpochMilli()),
                            command.requestId())
                    .next()
                    .then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    public Mono<Void> complete(String requestId) {
        return redis.execute(COMPLETE_SCRIPT, List.of(key(requestId), DUE_INDEX), requestId)
                .next()
                .then();
    }

    public Flux<RecoveryCommand> due(Instant now) {
        return redis.opsForZSet()
                .rangeByScore(
                        DUE_INDEX,
                        Range.closed(0.0, (double) now.toEpochMilli()),
                        Limit.limit().count(properties.recovery().batchSize())
                )
                .flatMap(requestId -> claim(requestId, now)
                        .filter(Boolean.TRUE::equals)
                        .flatMap(ignored -> load(requestId)));
    }

    private Mono<Boolean> claim(String requestId, Instant now) {
        long claimMillis = Math.max(30_000L, properties.recovery().scanInterval().toMillis() * 3L);
        long claimUntil = now.toEpochMilli() + claimMillis;
        return redis.execute(CLAIM_SCRIPT, List.of(DUE_INDEX),
                        requestId, Long.toString(now.toEpochMilli()), Long.toString(claimUntil))
                .next()
                .map(result -> result != null && result == 1L)
                .defaultIfEmpty(false);
    }

    private Mono<RecoveryCommand> load(String requestId) {
        return redis.opsForValue().get(key(requestId))
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, RecoveryCommand.class));
                    } catch (JsonProcessingException exception) {
                        return complete(requestId).then(Mono.empty());
                    }
                })
                .switchIfEmpty(complete(requestId).then(Mono.empty()));
    }

    private String key(String requestId) {
        return COMMAND_PREFIX + requestId;
    }
}
