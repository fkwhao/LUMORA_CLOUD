package com.lumora.cloud.modelgateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.concurrency.ConcurrencyPermit;
import com.lumora.cloud.modelgateway.concurrency.DistributedConcurrencyLimiter;
import com.lumora.cloud.modelgateway.concurrency.RequestLease;
import com.lumora.cloud.modelgateway.concurrency.RequestLeaseService;
import com.lumora.cloud.modelgateway.concurrency.RouteCapacityException;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.domain.model.TokenUsage;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
import com.lumora.cloud.modelgateway.domain.model.ValidatedChatRequest;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ModelProviderClient;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import com.lumora.cloud.modelgateway.provider.ProviderCredentialResolver;
import com.lumora.cloud.modelgateway.provider.ProviderHttpException;
import com.lumora.cloud.modelgateway.provider.StreamUsageTracker;
import com.lumora.cloud.modelgateway.recovery.BillingRecoveryService;
import com.lumora.cloud.modelgateway.routing.UpstreamRouteSelector;
import com.lumora.cloud.modelgateway.routing.RouteCircuitBreaker;
import com.lumora.cloud.modelgateway.routing.DistributedRouteRateLimiter;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.domain.vo.invoke.ModelGatewayResponse;
import com.lumora.cloud.modelgateway.billing.QuotaCalculator.PricingSnapshot;
import com.lumora.cloud.modelgateway.billing.BillingControlService;
import com.lumora.cloud.modelgateway.billing.QuotaCalculator;
import com.lumora.cloud.modelgateway.cache.ModelConfigCache;
import com.lumora.cloud.modelgateway.protocol.LumoraProtocolAdapter;
import com.lumora.cloud.modelgateway.service.IModelGatewayService;
import com.lumora.cloud.modelgateway.utils.RequestIds;
import com.lumora.cloud.modelgateway.validation.ChatRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
public class ModelGatewayServiceImpl implements IModelGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayServiceImpl.class);

    private final ChatRequestValidator validator;
    private final ModelConfigCache modelCache;
    private final ProviderCredentialResolver credentials;
    private final QuotaCalculator quotaCalculator;
    private final RequestIds requestIds;
    private final RequestLeaseService requestLeases;
    private final DistributedConcurrencyLimiter concurrencyLimiter;
    private final UpstreamRouteSelector routeSelector;
    private final RouteCircuitBreaker routeCircuitBreaker;
    private final DistributedRouteRateLimiter routeRateLimiter;
    private final BillingControlService billing;
    private final BillingRecoveryService recovery;
    private final ModelProviderClient providerClient;
    private final ProviderUsageParser usageParser;
    private final LumoraProtocolAdapter lumoraProtocol;
    private final ObjectMapper objectMapper;
    private final ModelGatewayProperties properties;

    public ModelGatewayServiceImpl(
            ChatRequestValidator validator,
            ModelConfigCache modelCache,
            ProviderCredentialResolver credentials,
            QuotaCalculator quotaCalculator,
            RequestIds requestIds,
            RequestLeaseService requestLeases,
            DistributedConcurrencyLimiter concurrencyLimiter,
            UpstreamRouteSelector routeSelector,
            RouteCircuitBreaker routeCircuitBreaker,
            DistributedRouteRateLimiter routeRateLimiter,
            BillingControlService billing,
            BillingRecoveryService recovery,
            ModelProviderClient providerClient,
            ProviderUsageParser usageParser,
            LumoraProtocolAdapter lumoraProtocol,
            ObjectMapper objectMapper,
            ModelGatewayProperties properties
    ) {
        this.validator = validator;
        this.modelCache = modelCache;
        this.credentials = credentials;
        this.quotaCalculator = quotaCalculator;
        this.requestIds = requestIds;
        this.requestLeases = requestLeases;
        this.concurrencyLimiter = concurrencyLimiter;
        this.routeSelector = routeSelector;
        this.routeCircuitBreaker = routeCircuitBreaker;
        this.routeRateLimiter = routeRateLimiter;
        this.billing = billing;
        this.recovery = recovery;
        this.providerClient = providerClient;
        this.usageParser = usageParser;
        this.lumoraProtocol = lumoraProtocol;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<ModelGatewayResponse> invoke(
            GatewayRequestContext context,
            JsonNode body,
            GatewayProtocol protocol
    ) {
        return Mono.defer(() -> {
            ValidatedChatRequest request = validator.parse(body, protocol);
            return requestLeases.acquire(context)
                    .flatMap(lease -> invokeWithLease(context, request, lease));
        });
    }

    private Mono<ModelGatewayResponse> invokeWithLease(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            RequestLease lease
    ) {
        return modelCache.resolve(request.modelCode())
                .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                .flatMap(model -> prepare(context, request, model, lease));
    }

    private Mono<ModelGatewayResponse> prepare(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            ResolvedModelConfig model,
            RequestLease lease
    ) {
        return Mono.fromCallable(() -> {
                    PricingSnapshot pricing = quotaCalculator.snapshot(model, Instant.now());
                    return new PreparedModel(
                            model, pricing,
                            quotaCalculator.maximum(model, request.requestedMaxOutputTokens(), pricing),
                            routeSelector.orderedCandidates(model)
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                .flatMap(prepared -> reserveAndInvoke(context, request, lease, prepared));
    }

    private Mono<ModelGatewayResponse> reserveAndInvoke(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            RequestLease lease,
            PreparedModel prepared
    ) {
        ReserveRequest reserve = new ReserveRequest(
                lease.billingRequestId(), context.clientRequestId(), context.userId(), prepared.model().modelCode(),
                prepared.model().pricingVersion(), prepared.maximumQuota(),
                prepared.pricing().pricingAt(), prepared.pricing().quotaMultiplier(), prepared.pricing().ruleName(),
                Instant.now().plus(properties.provider().maxCallDuration()).plusSeconds(30)
        );
        return billing.reserve(reserve)
                .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                .flatMap(reservation -> validateFreshReservation(reservation)
                        .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                        .then(selectAndInvoke(context, request, prepared, 0, null)
                                .onErrorResume(error -> handleReservedFailure(error, lease)))
                        .flatMap(selected -> request.stream()
                                ? Mono.just(streamingResponse(
                                        context, lease, selected.permit(), selected.call(), request,
                                        selected.provider()
                                ))
                                : bufferedResponse(
                                        context, lease, selected.permit(), selected.call(), request,
                                        selected.provider()
                                ).onErrorResume(error -> handleBeforeResponseFailure(
                                        error, lease, selected.permit()
                                ))));
    }

    private Mono<SelectedProvider> selectAndInvoke(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            PreparedModel prepared,
            int index,
            Throwable previousFailure
    ) {
        if (index >= prepared.routes().size()) {
            return Mono.error(previousFailure == null
                    ? new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_ROUTE_UNAVAILABLE",
                    "当前模型没有可用的上游路由")
                    : previousFailure);
        }
        ResolvedModelRoute route = prepared.routes().get(index);
        if (!request.protocol().isInternal()
                && ProviderProtocol.parse(route.protocolType()) != request.protocol().providerProtocol()) {
            return selectAndInvoke(context, request, prepared, index + 1, previousFailure);
        }
        return prepareRoute(request, prepared, route)
                .flatMap(call -> concurrencyLimiter.acquire(
                                context.userId(), prepared.model().modelCode(), route
                        )
                        .flatMap(permit -> routeRateLimiter.acquire(route, estimatedTokens(request, prepared.model()))
                                .then(routeCircuitBreaker.protect(
                                        route, () -> invokeProvider(context, request, call)
                                ))
                                .map(provider -> new SelectedProvider(call, permit, provider))
                                .onErrorResume(error -> concurrencyLimiter.release(permit)
                                        .then(Mono.error(error)))))
                .onErrorResume(error -> tryNextRoute(context, request, prepared, index, route, error));
    }

    private Mono<SelectedProvider> tryNextRoute(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            PreparedModel prepared,
            int index,
            ResolvedModelRoute route,
            Throwable error
    ) {
        if (route.failoverEnabled() && index + 1 < prepared.routes().size() && isFailoverEligible(error)) {
            log.warn("Model route failed over model={} routeId={} provider={} failure={}",
                    prepared.model().modelCode(), route.routeId(), route.providerCode(),
                    error.getClass().getSimpleName());
            return selectAndInvoke(context, request, prepared, index + 1, error);
        }
        return Mono.error(error);
    }

    private Mono<PreparedCall> prepareRoute(
            ValidatedChatRequest request,
            PreparedModel prepared,
            ResolvedModelRoute route
    ) {
        return Mono.fromCallable(() -> {
            ResolvedModelConfig routedModel = prepared.model().withRoute(route);
            ProviderProtocol protocol = ProviderProtocol.parse(route.protocolType());
            ObjectNode upstreamBody = request.protocol().isInternal()
                    ? lumoraProtocol.upstreamBody(
                    request.originalBody(), routedModel, protocol,
                    request.stream(), request.requestedMaxOutputTokens())
                    : validator.upstreamBody(request, routedModel);
            return new PreparedCall(
                    routedModel, route, upstreamBody, credentials.resolve(route.credentialReference()),
                    prepared.pricing(), prepared.maximumQuota(), protocol
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isFailoverEligible(Throwable error) {
        if (error instanceof RouteCapacityException) {
            return true;
        }
        if (error instanceof ProviderHttpException providerError) {
            int status = providerError.getStatus().value();
            return status == 401 || status == 403 || status == 408 || status == 429 || status >= 500;
        }
        return !(error instanceof ApiException);
    }

    private long estimatedTokens(ValidatedChatRequest request, ResolvedModelConfig model) {
        long estimatedInput = Math.max(1L, request.originalBody().toString().length() / 4L);
        long requestedOutput = request.requestedMaxOutputTokens() == Long.MAX_VALUE
                ? model.capabilities().maxOutputTokens()
                : Math.min(request.requestedMaxOutputTokens(), model.capabilities().maxOutputTokens());
        long total;
        try {
            total = Math.addExact(estimatedInput, requestedOutput);
        } catch (ArithmeticException ignored) {
            total = Long.MAX_VALUE;
        }
        return Math.max(1L, Math.min(total,
                model.capabilities().contextWindow() + model.capabilities().maxOutputTokens()));
    }

    private Mono<ProviderCall> invokeProvider(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            PreparedCall call
    ) {
        return Mono.defer(() -> providerClient.invoke(
                        call.model(), call.credential(), call.upstreamBody(), request.stream(), context.traceId()
                ))
                .onErrorResume(ProviderHttpException.class, error -> {
                    int status = error.getStatus().value();
                    String reference = call.model().credentialReference();
                    if ((status == 401 || status == 403) && reference.startsWith("cred_")) {
                        credentials.invalidate(reference);
                        return Mono.fromCallable(() -> credentials.resolve(reference))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(refreshed -> providerClient.invoke(
                                        call.model(), refreshed, call.upstreamBody(), request.stream(), context.traceId()
                                ));
                    }
                    return Mono.error(error);
                });
    }

    private Mono<Void> validateFreshReservation(ReservationResponse reservation) {
        if (!reservation.idempotentReplay() && reservation.status() == ReservationStatus.ACTIVE) {
            return Mono.empty();
        }
        return Mono.error(new ApiException(HttpStatus.CONFLICT, "MODEL_REQUEST_ALREADY_PROCESSED",
                "该客户端请求标识已经使用，请为新的模型调用生成新的标识"));
    }

    private Mono<ModelGatewayResponse> bufferedResponse(
            GatewayRequestContext context,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call,
            ValidatedChatRequest request,
            ProviderCall provider
    ) {
        return DataBufferUtils.join(
                        provider.body(),
                        properties.provider().maxBufferedResponseBytes()
                )
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> finalizeBuffered(context, lease, permit, call, call.upstreamProtocol(), bytes)
                        .map(ignored -> {
                            byte[] responseBytes = request.protocol().isInternal()
                                    ? lumoraProtocol.bufferedResponse(
                                            bytes, call.model(), call.upstreamProtocol(), context.traceId()
                                    )
                                    : bytes;
                            return new ModelGatewayResponse(
                                    provider.status(), responseHeaders(
                                            provider.headers(), context, call.model(), call.route(), false,
                                            request.protocol().isInternal()
                                    ),
                                    Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(responseBytes))
                            );
                        }));
    }

    private Mono<Boolean> finalizeBuffered(
            GatewayRequestContext context,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call,
            ProviderProtocol protocol,
            byte[] bytes
    ) {
        TokenUsage usage = null;
        try {
            usage = usageParser.parse(protocol, objectMapper.readTree(bytes));
        } catch (Exception ignored) {
        }
        return concurrencyLimiter.release(permit)
                .then(finalizeSuccess(context, lease, call, usage, "供应商成功响应缺少权威 Usage"));
    }

    private ModelGatewayResponse streamingResponse(
            GatewayRequestContext context,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call,
            ValidatedChatRequest request,
            ProviderCall provider
    ) {
        StreamUsageTracker tracker = new StreamUsageTracker(objectMapper, usageParser, call.upstreamProtocol());
        AtomicBoolean finalized = new AtomicBoolean();
        Flux<DataBuffer> monitored = provider.body()
                .map(buffer -> copyAndTrack(buffer, tracker));
        Flux<DataBuffer> translated = request.protocol().isInternal()
                ? lumoraProtocol.streamingResponse(
                        monitored, call.model(), call.upstreamProtocol(), context.traceId()
                )
                : monitored;
        Flux<DataBuffer> body = translated
                .concatWith(Flux.defer(() -> {
                    return releaseAndFinalizeOnce(finalized, lease, permit, () -> {
                        tracker.finish();
                        return finalizeSuccess(context, lease, call, tracker.usage(),
                                "供应商流式响应缺少权威 Usage");
                    }).thenMany(Flux.empty());
                }))
                .onErrorResume(error -> releaseAndFinalizeOnce(
                                finalized, lease, permit,
                                () -> finalizeStreamTermination(
                                        context, lease, call, tracker,
                                        "供应商流式响应中断，计费状态待确认"
                                )
                        )
                        .thenMany(Flux.error(error)))
                .doOnCancel(() -> releaseAndFinalizeOnce(
                        finalized, lease, permit,
                        () -> finalizeStreamTermination(
                                context, lease, call, tracker,
                                "客户端取消流式响应，供应商计费状态待确认"
                        )
                ).subscribe(ignored -> { }, error -> { }));
        return new ModelGatewayResponse(
                provider.status(), responseHeaders(
                        provider.headers(), context, call.model(), call.route(), true, request.protocol().isInternal()
                ), body
        );
    }

    private DataBuffer copyAndTrack(DataBuffer source, StreamUsageTracker tracker) {
        byte[] bytes = new byte[source.readableByteCount()];
        source.read(bytes);
        DataBufferUtils.release(source);
        tracker.accept(bytes);
        return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
    }

    private Mono<Boolean> finalizeSuccess(
            GatewayRequestContext context,
            RequestLease lease,
            PreparedCall call,
            TokenUsage usage,
            String missingUsageReason
    ) {
        Mono<Boolean> billingResult;
        if (usage == null || !usage.hasUsage()) {
            billingResult = recovery.pending(lease.billingRequestId(), missingUsageReason);
        } else {
            BigDecimal billedQuota = quotaCalculator.actual(call.model(), usage, call.pricing());
            SettleRequest settlement = new SettleRequest(
                    requestIds.usageId(lease.billingRequestId(), call.model().pricingVersion()),
                    call.model().pricingVersion(), usage.inputTokens(), usage.outputTokens(), usage.reasoningTokens(),
                    usage.cacheReadTokens(), usage.cacheWriteTokens(), billedQuota, Instant.now()
            );
            billingResult = recovery.settle(lease.billingRequestId(), settlement);
        }
        return finalizeAccounting(lease, billingResult);
    }

    private Mono<Boolean> finalizePending(RequestLease lease, String reason) {
        return finalizeAccounting(lease, recovery.pending(lease.billingRequestId(), reason));
    }

    private Mono<Boolean> finalizeStreamTermination(
            GatewayRequestContext context,
            RequestLease lease,
            PreparedCall call,
            StreamUsageTracker tracker,
            String missingUsageReason
    ) {
        tracker.finish();
        return tracker.usage() == null
                ? finalizePending(lease, missingUsageReason)
                : finalizeSuccess(context, lease, call, tracker.usage(), missingUsageReason);
    }

    private Mono<ModelGatewayResponse> handleBeforeResponseFailure(
            Throwable error,
            RequestLease lease,
            ConcurrencyPermit permit
    ) {
        if (error instanceof ApiException) {
            return concurrencyLimiter.release(permit)
                    .then(requestLeases.release(lease))
                    .then(Mono.error(error));
        }
        Mono<Boolean> billingResult;
        if (error instanceof ProviderHttpException providerError && providerError.isDefinitiveRejection()) {
            billingResult = recovery.release(lease.billingRequestId(), "供应商明确拒绝请求，未开始模型调用");
        } else {
            billingResult = recovery.pending(lease.billingRequestId(), "供应商调用结果不确定，需要对账");
        }
        return concurrencyLimiter.release(permit)
                .then(finalizeAccounting(lease, billingResult))
                .then(Mono.error(mapProviderError(error)));
    }

    private Mono<SelectedProvider> handleReservedFailure(Throwable error, RequestLease lease) {
        Mono<Boolean> billingResult;
        if (error instanceof ApiException) {
            billingResult = recovery.release(lease.billingRequestId(), "模型路由在调用上游前不可用");
        } else if (error instanceof ProviderHttpException providerError && providerError.isDefinitiveRejection()) {
            billingResult = recovery.release(lease.billingRequestId(), "供应商明确拒绝请求，未开始模型调用");
        } else {
            billingResult = recovery.pending(lease.billingRequestId(), "全部上游路由调用结果不确定，需要对账");
        }
        return billingResult
                .flatMap(completed -> completed ? requestLeases.release(lease) : Mono.empty())
                .then(Mono.error(mapProviderError(error)));
    }

    private Throwable mapProviderError(Throwable error) {
        if (error instanceof ApiException) {
            return error;
        }
        if (error instanceof ProviderHttpException providerError) {
            int status = providerError.getStatus().value();
            if (status == 429) {
                return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "UPSTREAM_RATE_LIMITED",
                        "模型供应商当前繁忙，请稍后重试", error);
            }
            if (providerError.isDefinitiveRejection()) {
                HttpStatus responseStatus = switch (status) {
                    case 400 -> HttpStatus.BAD_REQUEST;
                    case 413 -> HttpStatus.PAYLOAD_TOO_LARGE;
                    case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
                    default -> HttpStatus.BAD_GATEWAY;
                };
                return new ApiException(responseStatus,
                        "UPSTREAM_REQUEST_REJECTED", "模型供应商拒绝了请求参数", error);
            }
            return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_PROVIDER_ERROR",
                    "模型供应商暂时不可用", error);
        }
        if (error instanceof TimeoutException) {
            return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "模型供应商响应超时", error);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_CONNECTION_FAILED",
                "无法连接模型供应商", error);
    }

    private Mono<Boolean> releaseAndFinalizeOnce(
            AtomicBoolean finalized,
            RequestLease lease,
            ConcurrencyPermit permit,
            Supplier<Mono<Boolean>> operation
    ) {
        return concurrencyLimiter.release(permit)
                .then(Mono.defer(() -> {
                    if (!finalized.compareAndSet(false, true)) {
                        return Mono.just(true);
                    }
                    Mono<Boolean> durable = Mono.defer(operation).cache();
                    durable.subscribe(
                            ignored -> { },
                            error -> log.error(
                                    "Stream accounting finalization failed billingRequestId={}",
                                    lease.billingRequestId(), error
                            )
                    );
                    return durable;
                }));
    }

    private Mono<Boolean> finalizeAccounting(RequestLease lease, Mono<Boolean> billingResult) {
        return billingResult.flatMap(completed -> completed
                ? requestLeases.release(lease).thenReturn(true)
                : Mono.just(false));
    }

    private HttpHeaders responseHeaders(
            HttpHeaders providerHeaders,
            GatewayRequestContext context,
            ResolvedModelConfig model,
            ResolvedModelRoute route,
            boolean stream,
            boolean internalProtocol
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(providerHeaders);
        if (internalProtocol) {
            headers.setContentType(stream
                    ? org.springframework.http.MediaType.TEXT_EVENT_STREAM
                    : org.springframework.http.MediaType.APPLICATION_JSON);
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.remove(HttpHeaders.CONTENT_ENCODING);
        } else if (headers.getContentType() == null) {
            headers.setContentType(stream
                    ? org.springframework.http.MediaType.TEXT_EVENT_STREAM
                    : org.springframework.http.MediaType.APPLICATION_JSON);
        }
        headers.set(AuthHeaders.REQUEST_ID, context.traceId());
        headers.set("X-Lumora-Pricing-Version", model.pricingVersion());
        headers.set("X-Lumora-Provider-Code", model.providerCode());
        headers.set("X-Lumora-Route-Id", route.routeId());
        headers.set("X-Lumora-Route-Name", route.routeName());
        headers.setCacheControl("no-store");
        return headers;
    }

    private record PreparedCall(
            ResolvedModelConfig model,
            ResolvedModelRoute route,
            ObjectNode upstreamBody,
            String credential,
            PricingSnapshot pricing,
            BigDecimal maximumQuota,
            ProviderProtocol upstreamProtocol
    ) {
    }

    private record PreparedModel(
            ResolvedModelConfig model,
            PricingSnapshot pricing,
            BigDecimal maximumQuota,
            List<ResolvedModelRoute> routes
    ) {
    }

    private record SelectedProvider(
            PreparedCall call,
            ConcurrencyPermit permit,
            ProviderCall provider
    ) {
    }
}
