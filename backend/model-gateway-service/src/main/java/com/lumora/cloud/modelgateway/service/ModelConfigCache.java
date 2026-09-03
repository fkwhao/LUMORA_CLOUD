package com.lumora.cloud.modelgateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.fallback.RemoteServiceUnavailableException;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.codec.DecodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Locale;

@Component
public class ModelConfigCache {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigCache.class);

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
                .onErrorMap(FeignException.class, exception -> mapCatalogFailure(normalized, exception))
                .onErrorMap(RemoteServiceUnavailableException.class, exception -> {
                    log.warn(
                            "Model catalog protection activated modelCode={} service={} operation={}",
                            normalized, exception.getServiceName(), exception.getOperation()
                    );
                    return new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "MODEL_CATALOG_PROTECTED",
                            "模型目录服务正在熔断保护中，请稍后重试",
                            exception
                    );
                });
    }

    private ApiException mapCatalogFailure(String modelCode, FeignException exception) {
        CatalogFailure failure = classify(exception);
        if (exception instanceof FeignException.NotFound) {
            log.info(
                    "Model catalog lookup did not resolve modelCode={} category={} status={} method={} path={}",
                    modelCode, failure.category(), exception.status(), requestMethod(exception), requestPath(exception)
            );
        } else {
            log.warn(
                    "Model catalog request failed modelCode={} category={} status={} method={} path={} exceptionType={}",
                    modelCode, failure.category(), exception.status(), requestMethod(exception), requestPath(exception),
                    exception.getClass().getSimpleName()
            );
        }
        return new ApiException(failure.status(), failure.code(), failure.message(), exception);
    }

    private CatalogFailure classify(FeignException exception) {
        if (exception instanceof FeignException.NotFound) {
            return new CatalogFailure(
                    HttpStatus.NOT_FOUND, "MODEL_NOT_AVAILABLE", "MODEL_NOT_FOUND", "模型当前不可用"
            );
        }
        if (exception instanceof DecodeException) {
            return new CatalogFailure(
                    HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_DECODE_FAILED", "DECODE_FAILED",
                    "模型目录响应解析失败"
            );
        }
        if (exception instanceof RetryableException || exception.status() <= 0) {
            return new CatalogFailure(
                    HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_CONNECTION_FAILED", "CONNECTION_FAILED",
                    "模型目录服务连接失败"
            );
        }
        if (exception.status() == HttpStatus.UNAUTHORIZED.value()
                || exception.status() == HttpStatus.FORBIDDEN.value()) {
            return new CatalogFailure(
                    HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_AUTH_FAILED", "INTERNAL_AUTH_FAILED",
                    "模型目录服务内部认证失败"
            );
        }
        if (hasNoAvailableInstanceMessage(exception)) {
            return new CatalogFailure(
                    HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_NO_AVAILABLE_INSTANCE", "NO_AVAILABLE_INSTANCE",
                    "模型目录服务当前没有可用实例"
            );
        }
        if (exception.status() >= 500) {
            return new CatalogFailure(
                    HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_BAD_RESPONSE", "UPSTREAM_SERVER_ERROR",
                    "模型目录服务返回异常"
            );
        }
        return new CatalogFailure(
                HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CATALOG_REQUEST_REJECTED", "REQUEST_REJECTED",
                "模型目录服务拒绝了内部请求"
        );
    }

    private boolean hasNoAvailableInstanceMessage(FeignException exception) {
        String details = (exception.getMessage() + " " + exception.contentUTF8()).toLowerCase(Locale.ROOT);
        return details.contains("load balancer does not contain an instance")
                || details.contains("no servers available for service")
                || details.contains("no available service instance");
    }

    private String requestMethod(FeignException exception) {
        Request request = exception.request();
        return request == null ? "UNKNOWN" : request.httpMethod().name();
    }

    private String requestPath(FeignException exception) {
        Request request = exception.request();
        if (request == null || request.url() == null) {
            return "UNKNOWN";
        }
        try {
            String path = URI.create(request.url()).getPath();
            return path == null || path.isBlank() ? "/" : path;
        } catch (IllegalArgumentException ignored) {
            return "UNAVAILABLE";
        }
    }

    private record CatalogFailure(HttpStatus status, String code, String category, String message) {
    }
}
