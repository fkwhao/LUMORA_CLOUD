package com.lumora.cloud.modelgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.ReserveRequest;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.concurrency.ConcurrencyPermit;
import com.lumora.cloud.modelgateway.concurrency.DistributedConcurrencyLimiter;
import com.lumora.cloud.modelgateway.concurrency.RequestLease;
import com.lumora.cloud.modelgateway.concurrency.RequestLeaseService;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import com.lumora.cloud.modelgateway.domain.ValidatedChatRequest;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ModelProviderClient;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import com.lumora.cloud.modelgateway.provider.ProviderCredentialResolver;
import com.lumora.cloud.modelgateway.provider.ProviderHttpException;
import com.lumora.cloud.modelgateway.provider.StreamUsageTracker;
import com.lumora.cloud.modelgateway.recovery.BillingRecoveryService;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.web.ModelGatewayResponse;
import com.lumora.cloud.modelgateway.service.QuotaCalculator.PricingSnapshot;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ModelGatewayOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayOrchestrator.class);

    private final ChatRequestValidator validator;
    private final ModelConfigCache modelCache;
    private final ProviderCredentialResolver credentials;
    private final QuotaCalculator quotaCalculator;
    private final RequestIds requestIds;
    private final RequestLeaseService requestLeases;
    private final DistributedConcurrencyLimiter concurrencyLimiter;
    private final BillingControlService billing;
    private final BillingRecoveryService recovery;
    private final ModelProviderClient providerClient;
    private final ProviderUsageParser usageParser;
    private final ObjectMapper objectMapper;
    private final ModelGatewayProperties properties;

    public ModelGatewayOrchestrator(
            ChatRequestValidator validator,
            ModelConfigCache modelCache,
            ProviderCredentialResolver credentials,
            QuotaCalculator quotaCalculator,
            RequestIds requestIds,
            RequestLeaseService requestLeases,
            DistributedConcurrencyLimiter concurrencyLimiter,
            BillingControlService billing,
            BillingRecoveryService recovery,
            ModelProviderClient providerClient,
            ProviderUsageParser usageParser,
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
        this.billing = billing;
        this.recovery = recovery;
        this.providerClient = providerClient;
        this.usageParser = usageParser;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Mono<ModelGatewayResponse> invoke(
            GatewayRequestContext context,
            JsonNode body,
            ProviderProtocol protocol
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
                    return new PreparedCall(
                            model,
                            validator.upstreamBody(request, model),
                            credentials.resolve(model.credentialReference()),
                            pricing,
                            quotaCalculator.maximum(model, request.requestedMaxOutputTokens(), pricing)
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                .flatMap(prepared -> concurrencyLimiter.acquire(context.userId(), model.modelCode())
                        .onErrorResume(error -> requestLeases.release(lease).then(Mono.error(error)))
                        .flatMap(permit -> reserveAndInvoke(context, request, lease, permit, prepared)));
    }

    private Mono<ModelGatewayResponse> reserveAndInvoke(
            GatewayRequestContext context,
            ValidatedChatRequest request,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call
    ) {
        ReserveRequest reserve = new ReserveRequest(
                lease.billingRequestId(), context.clientRequestId(), context.userId(), call.model().modelCode(),
                call.model().pricingVersion(), call.maximumQuota(),
                call.pricing().pricingAt(), call.pricing().quotaMultiplier(), call.pricing().ruleName(),
                Instant.now().plus(properties.provider().maxCallDuration()).plusSeconds(30)
        );
        return billing.reserve(reserve)
                .flatMap(reservation -> validateFreshReservation(reservation)
                        .then(invokeProvider(context, request, call))
                        .flatMap(provider -> request.stream()
                                ? Mono.just(streamingResponse(
                                        context, lease, permit, call, request.protocol(), provider
                                ))
                                : bufferedResponse(
                                        context, lease, permit, call, request.protocol(), provider
                                )))
                .onErrorResume(error -> handleBeforeResponseFailure(error, lease, permit));
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
            ProviderProtocol protocol,
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
                .flatMap(bytes -> finalizeBuffered(context, lease, permit, call, protocol, bytes)
                        .map(ignored -> new ModelGatewayResponse(
                                provider.status(), responseHeaders(provider.headers(), context, call.model(), false),
                                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))
                        )));
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
        return finalizeSuccess(context, lease, permit, call, usage, "供应商成功响应缺少权威 Usage");
    }

    private ModelGatewayResponse streamingResponse(
            GatewayRequestContext context,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call,
            ProviderProtocol protocol,
            ProviderCall provider
    ) {
        StreamUsageTracker tracker = new StreamUsageTracker(objectMapper, usageParser, protocol);
        AtomicBoolean finalized = new AtomicBoolean();
        Flux<DataBuffer> monitored = provider.body()
                .map(buffer -> copyAndTrack(buffer, tracker));
        Flux<DataBuffer> body = monitored
                .concatWith(Flux.defer(() -> {
                    tracker.finish();
                    return finalizeOnce(
                            finalized,
                            finalizeSuccess(context, lease, permit, call, tracker.usage(),
                                    "供应商流式响应缺少权威 Usage")
                    ).thenMany(Flux.empty());
                }))
                .onErrorResume(error -> finalizeOnce(finalized, finalizeStreamTermination(
                                context, lease, permit, call, tracker,
                                "供应商流式响应中断，计费状态待确认"
                        ))
                        .thenMany(Flux.error(error)))
                .doOnCancel(() -> finalizeOnce(
                        finalized,
                        finalizeStreamTermination(
                                context, lease, permit, call, tracker,
                                "客户端取消流式响应，供应商计费状态待确认"
                        )
                ).subscribe());
        return new ModelGatewayResponse(
                provider.status(), responseHeaders(provider.headers(), context, call.model(), true), body
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
            ConcurrencyPermit permit,
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
        return billingResult.flatMap(completed -> cleanup(lease, permit, completed).thenReturn(completed));
    }

    private Mono<Boolean> finalizePending(RequestLease lease, ConcurrencyPermit permit, String reason) {
        return recovery.pending(lease.billingRequestId(), reason)
                .flatMap(completed -> cleanup(lease, permit, completed).thenReturn(completed));
    }

    private Mono<Boolean> finalizeStreamTermination(
            GatewayRequestContext context,
            RequestLease lease,
            ConcurrencyPermit permit,
            PreparedCall call,
            StreamUsageTracker tracker,
            String missingUsageReason
    ) {
        tracker.finish();
        return tracker.usage() == null
                ? finalizePending(lease, permit, missingUsageReason)
                : finalizeSuccess(context, lease, permit, call, tracker.usage(), missingUsageReason);
    }

    private Mono<ModelGatewayResponse> handleBeforeResponseFailure(
            Throwable error,
            RequestLease lease,
            ConcurrencyPermit permit
    ) {
        if (error instanceof ApiException) {
            return cleanup(lease, permit, true).then(Mono.error(error));
        }
        Mono<Boolean> billingResult;
        if (error instanceof ProviderHttpException providerError && providerError.isDefinitiveRejection()) {
            billingResult = recovery.release(lease.billingRequestId(), "供应商明确拒绝请求，未开始模型调用");
        } else {
            billingResult = recovery.pending(lease.billingRequestId(), "供应商调用结果不确定，需要对账");
        }
        return billingResult
                .flatMap(completed -> cleanup(lease, permit, completed))
                .then(Mono.error(mapProviderError(error)));
    }

    private Throwable mapProviderError(Throwable error) {
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

    private Mono<Boolean> finalizeOnce(AtomicBoolean finalized, Mono<Boolean> operation) {
        return finalized.compareAndSet(false, true) ? operation : Mono.just(true);
    }

    private Mono<Void> cleanup(
            RequestLease lease,
            ConcurrencyPermit permit,
            boolean releaseRequestLease
    ) {
        Mono<Void> releaseConcurrency = concurrencyLimiter.release(permit);
        return releaseRequestLease
                ? Mono.whenDelayError(releaseConcurrency, requestLeases.release(lease)).onErrorResume(error -> Mono.empty())
                : releaseConcurrency;
    }

    private HttpHeaders responseHeaders(
            HttpHeaders providerHeaders,
            GatewayRequestContext context,
            ResolvedModelConfig model,
            boolean stream
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(providerHeaders);
        if (headers.getContentType() == null) {
            headers.setContentType(stream
                    ? org.springframework.http.MediaType.TEXT_EVENT_STREAM
                    : org.springframework.http.MediaType.APPLICATION_JSON);
        }
        headers.set(AuthHeaders.REQUEST_ID, context.traceId());
        headers.set("X-Lumora-Pricing-Version", model.pricingVersion());
        headers.set("X-Lumora-Provider-Code", model.providerCode());
        headers.setCacheControl("no-store");
        return headers;
    }

    private record PreparedCall(
            ResolvedModelConfig model,
            ObjectNode upstreamBody,
            String credential,
            PricingSnapshot pricing,
            BigDecimal maximumQuota
    ) {
    }
}
