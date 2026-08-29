package com.lumora.cloud.modelgateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;

@Component
public class ModelConfigCache {

    private final CatalogClient catalogClient;
    private final Cache<String, ResolvedModelConfig> cache;

    public ModelConfigCache(CatalogClient catalogClient, ModelGatewayProperties properties) {
        this.catalogClient = catalogClient;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.catalog().maximumSize())
                .expireAfterWrite(properties.catalog().localCacheTtl())
                .build();
    }

    public Mono<ResolvedModelConfig> resolve(String modelCode) {
        String normalized = modelCode.trim().toLowerCase(Locale.ROOT);
        return Mono.fromCallable(() -> cache.get(normalized, catalogClient::resolve))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_INVALID_RESPONSE",
                        "模型目录返回了空配置"
                )))
                .onErrorMap(FeignException.NotFound.class, exception ->
                        new ApiException(HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE", "模型当前不可用", exception))
                .onErrorMap(throwable -> throwable instanceof FeignException
                                && !(throwable instanceof FeignException.NotFound),
                        throwable -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_UNAVAILABLE",
                                "模型目录服务暂时不可用", throwable));
    }
}
