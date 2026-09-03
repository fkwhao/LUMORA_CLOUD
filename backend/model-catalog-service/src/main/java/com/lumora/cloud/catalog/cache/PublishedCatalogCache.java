package com.lumora.cloud.catalog.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class PublishedCatalogCache {

    private static final Logger log = LoggerFactory.getLogger(PublishedCatalogCache.class);
    private static final String CACHE_KEY = "lumora:catalog:published:v3";
    private static final String GENERATION_KEY = "lumora:catalog:generation";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final ReentrantLock localLoadLock = new ReentrantLock();

    public PublishedCatalogCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${lumora.catalog.cache-ttl:PT5M}") Duration ttl
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public List<ResolvedModelConfig> getOrLoad(Supplier<List<ResolvedModelConfig>> loader) {
        CacheEnvelope cached = read();
        if (cached != null) {
            return cached.models();
        }
        localLoadLock.lock();
        try {
            cached = read();
            if (cached != null) {
                return cached.models();
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                Long before = generation();
                List<ResolvedModelConfig> models = List.copyOf(loader.get());
                Long after = generation();
                if (before == null || after == null) {
                    return models;
                }
                if (before.equals(after)) {
                    write(new CacheEnvelope(after, models));
                    return models;
                }
            }
            return List.copyOf(loader.get());
        } finally {
            localLoadLock.unlock();
        }
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

    private CacheEnvelope read() {
        try {
            String json = redis.opsForValue().get(CACHE_KEY);
            if (json == null) {
                return null;
            }
            CacheEnvelope envelope = objectMapper.readValue(json, CacheEnvelope.class);
            Long currentGeneration = generation();
            return currentGeneration != null && currentGeneration.longValue() == envelope.generation() ? envelope : null;
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Published model cache read failed; falling back to MySQL", exception);
            return null;
        }
    }

    private void write(CacheEnvelope envelope) {
        try {
            redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(envelope), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Published model cache write failed; continuing without cache", exception);
        }
    }

    private Long generation() {
        try {
            String value = redis.opsForValue().get(GENERATION_KEY);
            return value == null ? 0L : Long.valueOf(value);
        } catch (RuntimeException exception) {
            log.warn("Published model cache generation read failed", exception);
            return null;
        }
    }

    private void evictNow() {
        try {
            redis.opsForValue().increment(GENERATION_KEY);
            redis.delete(CACHE_KEY);
        } catch (RuntimeException exception) {
            log.warn("Published model cache eviction failed", exception);
        }
    }

    public record CacheEnvelope(long generation, List<ResolvedModelConfig> models) {
        public CacheEnvelope {
            models = List.copyOf(models);
        }
    }
}
