package com.lumora.cloud.modelgateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.catalog.CatalogContracts.CostRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelRoute;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
import com.lumora.cloud.modelgateway.billing.BillingControlService;
import com.lumora.cloud.modelgateway.billing.QuotaCalculator;
import com.lumora.cloud.modelgateway.cache.ModelConfigCache;
import com.lumora.cloud.modelgateway.concurrency.ConcurrencyPermit;
import com.lumora.cloud.modelgateway.concurrency.DistributedConcurrencyLimiter;
import com.lumora.cloud.modelgateway.concurrency.RequestLease;
import com.lumora.cloud.modelgateway.concurrency.RequestLeaseService;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.config.RouteProtectionProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ModelProviderClient;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import com.lumora.cloud.modelgateway.provider.ProviderCredentialResolver;
import com.lumora.cloud.modelgateway.provider.ProviderHttpException;
import com.lumora.cloud.modelgateway.protocol.LumoraProtocolAdapter;
import com.lumora.cloud.modelgateway.recovery.BillingRecoveryService;
import com.lumora.cloud.modelgateway.routing.RouteCircuitBreaker;
import com.lumora.cloud.modelgateway.routing.DistributedRouteRateLimiter;
import com.lumora.cloud.modelgateway.routing.UpstreamRouteSelector;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.utils.RequestIds;
import com.lumora.cloud.modelgateway.validation.ChatRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ModelGatewayServiceImplTest {

    @Mock private ModelConfigCache modelCache;
    @Mock private ProviderCredentialResolver credentials;
    @Mock private RequestLeaseService requestLeases;
    @Mock private DistributedConcurrencyLimiter concurrencyLimiter;
    @Mock private BillingControlService billing;
    @Mock private BillingRecoveryService recovery;
    @Mock private ModelProviderClient providerClient;
    @Mock private DistributedRouteRateLimiter routeRateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestIds requestIds = new RequestIds();
    private ModelGatewayServiceImpl orchestrator;
    private ResolvedModelConfig model;
    private RequestLease lease;
    private ConcurrencyPermit permit;
    private GatewayRequestContext context;

    @BeforeEach
    void setUp() {
        lenient().when(recovery.ensureAvailable()).thenReturn(Mono.empty());
        model = model();
        lease = new RequestLease("request-key", "token", "mgw-request");
        permit = new ConcurrencyPermit("user-key", "model-key", "permit");
        context = new GatewayRequestContext(42L, "session", "device", "DESKTOP", "trace", "client-12345678");
        orchestrator = new ModelGatewayServiceImpl(
                new ChatRequestValidator(), modelCache, credentials, new QuotaCalculator(), requestIds,
                requestLeases, concurrencyLimiter, new UpstreamRouteSelector(),
                new RouteCircuitBreaker(new RouteProtectionProperties()), routeRateLimiter,
                billing, recovery, providerClient,
                new ProviderUsageParser(), new LumoraProtocolAdapter(objectMapper, new ProviderUsageParser()),
                objectMapper, properties()
        );
        when(requestLeases.acquire(context)).thenReturn(Mono.just(lease));
        when(requestLeases.release(any())).thenReturn(Mono.empty());
        when(modelCache.resolve("test-model")).thenReturn(Mono.just(model));
        lenient().when(credentials.resolve("TEST_KEY")).thenReturn("secret");
        lenient().when(concurrencyLimiter.acquire(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("test-model"), any())).thenReturn(Mono.just(permit));
        lenient().when(concurrencyLimiter.release(any())).thenReturn(Mono.empty());
        lenient().when(routeRateLimiter.acquire(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(Mono.empty());
        when(billing.reserve(any())).thenReturn(Mono.just(new ReservationResponse(
                "reservation", "mgw-request", 42L, "test-model", "pricing-v1",
                ReservationStatus.ACTIVE, amount("1"), null, amount("9"), Instant.now(), amount("1"), null,
                Instant.now().plusSeconds(60), false
        )));
    }

    @Test
    void unavailableRecoveryStorageStopsBeforeReservationAndProviderCall() throws Exception {
        reset(billing);
        when(recovery.ensureAvailable()).thenReturn(Mono.error(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_RECOVERY_UNAVAILABLE", "journal full")));
        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                {"model":"test-model","messages":[]}
                """), GatewayProtocol.OPENAI_COMPATIBLE)).expectErrorSatisfies(error ->
                assertThat(((ApiException) error).getCode()).isEqualTo("BILLING_RECOVERY_UNAVAILABLE")).verify();
        verifyNoInteractions(billing, providerClient);
        verify(requestLeases).release(lease);
    }

    @Test
    void settlesAuthoritativeNonStreamingUsageBeforeReturningResponse() throws Exception {
        byte[] response = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":100,"completion_tokens":20}}
                """.getBytes(StandardCharsets.UTF_8);
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.just(new ProviderCall(
                        HttpStatus.OK, new HttpHeaders(),
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(response))
                )));
        when(recovery.settle(any(), any())).thenReturn(Mono.just(true));

        Mono<String> result = orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[],"max_tokens":100}
                        """), GatewayProtocol.OPENAI_COMPATIBLE)
                .flatMap(call -> org.springframework.core.io.buffer.DataBufferUtils.join(call.body()))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                });

        StepVerifier.create(result)
                .assertNext(body -> assertThat(body).contains("\"content\":\"ok\""))
                .verifyComplete();

        ArgumentCaptor<SettleRequest> settlement = ArgumentCaptor.forClass(SettleRequest.class);
        verify(recovery).settle(org.mockito.ArgumentMatchers.eq("mgw-request"), settlement.capture());
        assertThat(settlement.getValue().inputTokens()).isEqualTo(100);
        assertThat(settlement.getValue().outputTokens()).isEqualTo(20);
        assertThat(settlement.getValue().billedQuota()).isEqualByComparingTo("0.001400");
        verify(concurrencyLimiter).release(permit);
        verify(requestLeases).release(lease);
    }

    @Test
    void releasesReservationWhenProviderDefinitivelyRejectsRequest() throws Exception {
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.error(new ProviderHttpException(HttpStatus.BAD_REQUEST)));
        when(recovery.release("mgw-request", "供应商明确拒绝请求，未开始模型调用"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[]}
                        """), GatewayProtocol.OPENAI_COMPATIBLE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("UPSTREAM_REQUEST_REJECTED");
                })
                .verify();

        verify(recovery).release("mgw-request", "供应商明确拒绝请求，未开始模型调用");
    }

    @Test
    void settlesStreamingUsageOnlyAfterTerminalEventIsConsumed() throws Exception {
        byte[] response = ("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20}}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.just(new ProviderCall(
                        HttpStatus.OK, new HttpHeaders(),
                        Flux.just(
                                DefaultDataBufferFactory.sharedInstance.wrap(
                                        java.util.Arrays.copyOfRange(response, 0, 31)
                                ),
                                DefaultDataBufferFactory.sharedInstance.wrap(
                                        java.util.Arrays.copyOfRange(response, 31, response.length)
                                )
                        )
                )));
        when(recovery.settle(any(), any())).thenReturn(Mono.just(true));

        Mono<Long> result = orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[],"stream":true,"max_tokens":100}
                        """), GatewayProtocol.OPENAI_COMPATIBLE)
                .flatMapMany(call -> call.body())
                .map(buffer -> {
                    long size = buffer.readableByteCount();
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                    return size;
                })
                .reduce(0L, Long::sum);

        StepVerifier.create(result)
                .expectNext((long) response.length)
                .verifyComplete();

        ArgumentCaptor<SettleRequest> settlement = ArgumentCaptor.forClass(SettleRequest.class);
        verify(recovery).settle(org.mockito.ArgumentMatchers.eq("mgw-request"), settlement.capture());
        assertThat(settlement.getValue().inputTokens()).isEqualTo(100);
        assertThat(settlement.getValue().outputTokens()).isEqualTo(20);
    }

    @Test
    void releasesConcurrencyBeforeAccountingWhenStreamingClientCancels() throws Exception {
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        byte[] firstChunk = "data: {\"choices\":[{\"delta\":{\"content\":\"tool\"}}]}\n\n"
                .getBytes(StandardCharsets.UTF_8);
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.just(new ProviderCall(
                        HttpStatus.OK, new HttpHeaders(),
                        Flux.concat(
                                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(firstChunk)),
                                Flux.never()
                        )
                )));
        when(concurrencyLimiter.release(permit))
                .thenReturn(Mono.fromRunnable(() -> lifecycle.add("concurrency-released")));
        when(recovery.pending("mgw-request", "客户端取消流式响应，供应商计费状态待确认"))
                .thenReturn(Mono.fromSupplier(() -> {
                    lifecycle.add("accounting-started");
                    return true;
                }));

        Flux<Integer> result = orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[],"stream":true,"max_tokens":100}
                        """), GatewayProtocol.OPENAI_COMPATIBLE)
                .flatMapMany(call -> call.body().take(1))
                .map(buffer -> {
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
                    return 1;
                });

        StepVerifier.create(result)
                .expectNext(1)
                .verifyComplete();

        assertThat(lifecycle).containsExactly("concurrency-released", "accounting-started");
        verify(requestLeases).release(lease);
    }

    @Test
    void neverCallsProviderForIdempotentReservationReplay() throws Exception {
        when(billing.reserve(any())).thenReturn(Mono.just(new ReservationResponse(
                "reservation", "mgw-request", 42L, "test-model", "pricing-v1",
                ReservationStatus.ACTIVE, amount("1"), null, amount("9"), Instant.now(), amount("1"), null,
                Instant.now().plusSeconds(60), true
        )));

        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[]}
                        """), GatewayProtocol.OPENAI_COMPATIBLE))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("MODEL_REQUEST_ALREADY_PROCESSED");
                })
                .verify();

        verifyNoInteractions(providerClient);
    }

    @Test
    void refreshesManagedCredentialOnceAfterUpstreamAuthenticationFailure() throws Exception {
        reset(credentials);
        ResolvedModelConfig managedModel = new ResolvedModelConfig(
                model.modelCode(), model.displayName(), model.description(), model.pricingVersion(),
                model.providerCode(), model.protocolType(), model.baseUrl(), "cred_test", model.upstreamModel(),
                model.capabilities(), model.costCurrency(), model.costRates(), model.costTimePricingPolicy(),
                model.quotaRates(), model.quotaTimePricingPolicy(), model.publishedAt()
        );
        when(modelCache.resolve("test-model")).thenReturn(Mono.just(managedModel));
        when(credentials.resolve("cred_test")).thenReturn("old-secret", "new-secret");
        byte[] response = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":10,"completion_tokens":2}}
                """.getBytes(StandardCharsets.UTF_8);
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.error(new ProviderHttpException(HttpStatus.UNAUTHORIZED)))
                .thenReturn(Mono.just(new ProviderCall(
                        HttpStatus.OK, new HttpHeaders(),
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(response))
                )));
        when(recovery.settle(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[]}
                        """), GatewayProtocol.OPENAI_COMPATIBLE))
                .expectNextCount(1)
                .verifyComplete();

        verify(credentials).invalidate("cred_test");
        verify(credentials, times(2)).resolve("cred_test");
        verify(providerClient, times(2)).invoke(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void failsOverToNextPriorityRouteBeforeReturningResponse() throws Exception {
        ResolvedModelRoute primary = route("route-a", "provider-a", "KEY_A", 10);
        ResolvedModelRoute secondary = route("route-b", "provider-b", "KEY_B", 20);
        ResolvedModelConfig routed = new ResolvedModelConfig(
                model.modelCode(), model.displayName(), model.description(), model.pricingVersion(),
                model.providerCode(), model.protocolType(), model.baseUrl(), model.credentialReference(),
                model.upstreamModel(), model.capabilities(), model.costCurrency(), model.costRates(),
                model.costTimePricingPolicy(), model.quotaRates(), model.quotaTimePricingPolicy(),
                model.publishedAt(), List.of(primary, secondary)
        );
        when(modelCache.resolve("test-model")).thenReturn(Mono.just(routed));
        when(credentials.resolve("KEY_A")).thenReturn("secret-a");
        when(credentials.resolve("KEY_B")).thenReturn("secret-b");
        byte[] response = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":10,"completion_tokens":2}}
                """.getBytes(StandardCharsets.UTF_8);
        when(providerClient.invoke(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.error(new ProviderHttpException(HttpStatus.SERVICE_UNAVAILABLE)))
                .thenReturn(Mono.just(new ProviderCall(
                        HttpStatus.OK, new HttpHeaders(),
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(response))
                )));
        when(recovery.settle(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[]}
                        """), GatewayProtocol.OPENAI_COMPATIBLE))
                .assertNext(result -> {
                    assertThat(result.headers().getFirst("X-Lumora-Provider-Code")).isEqualTo("provider-b");
                    assertThat(result.headers().getFirst("X-Lumora-Route-Id")).isEqualTo("route-b");
                })
                .verifyComplete();

        ArgumentCaptor<ResolvedModelConfig> calledModels = ArgumentCaptor.forClass(ResolvedModelConfig.class);
        verify(providerClient, times(2)).invoke(calledModels.capture(), any(), any(), anyBoolean(), any());
        assertThat(calledModels.getAllValues()).extracting(ResolvedModelConfig::providerCode)
                .containsExactly("provider-a", "provider-b");
    }

    private ResolvedModelConfig model() {
        return new ResolvedModelConfig(
                "test-model", "Test", null, "pricing-v1", "provider", "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", "TEST_KEY", "upstream-model",
                new ModelCapabilities(1_000, 100, true, true, true, true, false),
                "USD", new CostRates(amount("1"), amount("1"), amount("1"), amount("2")), null,
                new QuotaRates(amount("10"), amount("10"), amount("10"), amount("20"), amount("0.000001")),
                null, Instant.now()
        );
    }

    private ResolvedModelRoute route(String id, String providerCode, String credential, int priority) {
        return new ResolvedModelRoute(
                id, providerCode, (long) priority, providerCode, "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", credential, "upstream-model", priority, 100,
                10, null, null, 20, null, null, true, true,
                "USD", model.costRates(), null
        );
    }

    private ModelGatewayProperties properties() {
        return new ModelGatewayProperties(
                new ModelGatewayProperties.Catalog(Duration.ofSeconds(30), 100),
                new ModelGatewayProperties.Concurrency(3, 100, Duration.ofMinutes(5), Duration.ofMinutes(10)),
                new ModelGatewayProperties.Provider(
                        Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofMinutes(3),
                        100, 100, Duration.ofSeconds(3), Duration.ofMinutes(2), 1_048_576, 16_777_216
                ),
                new ModelGatewayProperties.Recovery(Duration.ofDays(7), Duration.ofSeconds(10), 100)
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
