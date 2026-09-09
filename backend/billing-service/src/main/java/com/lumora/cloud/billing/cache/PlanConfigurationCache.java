package com.lumora.cloud.billing.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.billing.domain.vo.plan.PlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class PlanConfigurationCache {

    private static final Logger log = LoggerFactory.getLogger(PlanConfigurationCache.class);
    private static final String PREFIX = "lumora:billing:plans:v1:";
    static final String GENERATION_KEY = PREFIX + "generation";
    private static final TypeReference<List<PlanResponse>> PLANS = new TypeReference<>() {};
    private static final TypeReference<PlanResponse> VERSION = new TypeReference<>() {};
    private static final TypeReference<ModelAccess> ACCESS = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final TransactionTemplate databaseRead;
    private final ReentrantLock loadLock = new ReentrantLock();

    public PlanConfigurationCache(StringRedisTemplate redis, ObjectMapper mapper,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${lumora.billing.plan-cache-ttl:PT5M}") Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lumora.billing.plan-cache-ttl must be positive");
        }
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
        this.databaseRead = new TransactionTemplate(transactionManager);
        this.databaseRead.setReadOnly(true);
    }

    public List<PlanResponse> published(Supplier<List<PlanResponse>> loader) {
        return List.copyOf(getOrLoad("published", PLANS, loader, false));
    }

    public PlanResponse version(Long versionId, Supplier<PlanResponse> loader) {
        return getOrLoad("version:" + versionId, VERSION, loader, true);
    }

    public ModelAccess modelAccess(Long versionId, Supplier<ModelAccess> loader) {
        return getOrLoad("access:" + versionId, ACCESS, loader, true);
    }

    private <T> T getOrLoad(String suffix, TypeReference<T> type, Supplier<T> loader, boolean immutableVersion) {
        String generation = generation();
        if (generation == null) {
            return load(loader);
        }
        T cached = read(generation, suffix, type);
        if (cached != null) {
            return cached;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            T value = load(loader);
            // Published versions are append-only. Delay their fill until commit so
            // uncommitted versions cannot leak. Never fill the mutable latest-version
            // list from an existing transaction's potentially old repeatable-read snapshot.
            if (immutableVersion && value != null && TransactionSynchronizationManager.isSynchronizationActive()) {
                String expectedGeneration = generation;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (expectedGeneration.equals(generation())) {
                            write(expectedGeneration, suffix, value);
                        }
                    }
                });
            }
            return value;
        }
        // Contending misses can read MySQL without waiting for a slow cache fill.
        if (!loadLock.tryLock()) {
            return load(loader);
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                generation = generation();
                if (generation == null) {
                    return load(loader);
                }
                cached = read(generation, suffix, type);
                if (cached != null) {
                    return cached;
                }
                T value = load(loader);
                if (generation.equals(generation())) {
                    if (value != null) {
                        write(generation, suffix, value);
                    }
                    return value;
                }
            }
            return load(loader);
        } finally {
            loadLock.unlock();
        }
    }

    private <T> T load(Supplier<T> loader) {
        // Reuse an existing transaction; do not acquire a second connection while
        // quota/settlement locks are held. Standalone reads get a read-only transaction.
        return databaseRead.execute(status -> loader.get());
    }

    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow();
                }
            });
        } else {
            evictNow();
        }
    }

    private void evictNow() {
        try {
            // Old in-flight fills stay in their old namespace and expire with their TTL.
            redis.opsForValue().set(GENERATION_KEY, UUID.randomUUID().toString());
        } catch (RuntimeException exception) {
            log.warn("Plan cache invalidation failed; cached entries remain bounded by TTL", exception);
        }
    }

    private String generation() {
        try {
            String value = redis.opsForValue().get(GENERATION_KEY);
            if (value == null) {
                redis.opsForValue().setIfAbsent(GENERATION_KEY, UUID.randomUUID().toString());
                value = redis.opsForValue().get(GENERATION_KEY);
            }
            return value;
        } catch (RuntimeException exception) {
            log.warn("Plan cache unavailable; falling back to MySQL", exception);
            return null;
        }
    }

    private <T> T read(String generation, String suffix, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(PREFIX + generation + ":" + suffix);
            return json == null ? null : mapper.readValue(json, type);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Plan cache entry unreadable; falling back to MySQL", exception);
            return null;
        }
    }

    private void write(String generation, String suffix, Object value) {
        try {
            redis.opsForValue().set(PREFIX + generation + ":" + suffix, mapper.writeValueAsString(value), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Plan cache fill failed; continuing with MySQL result", exception);
        }
    }

    public record ModelAccess(String mode, List<String> modelCodes) {
        public ModelAccess {
            modelCodes = List.copyOf(modelCodes);
        }
    }
}
