package com.lumora.cloud.modelgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.billing.BillingContracts.ReservationResponse;
import com.lumora.cloud.api.billing.BillingContracts.ReservationStatus;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.concurrency.ConcurrencyPermit;
import com.lumora.cloud.modelgateway.concurrency.DistributedConcurrencyLimiter;
import com.lumora.cloud.modelgateway.concurrency.RequestLease;
import com.lumora.cloud.modelgateway.concurrency.RequestLeaseService;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ModelProviderClient;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import com.lumora.cloud.modelgateway.provider.ProviderCall;
import com.lumora.cloud.modelgateway.provider.ProviderCredentialResolver;
import com.lumora.cloud.modelgateway.provider.ProviderHttpException;
import com.lumora.cloud.modelgateway.recovery.BillingRecoveryService;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelGatewayOrchestratorTest {

    @Mock private ModelConfigCache modelCache;
    @Mock private ProviderCredentialResolver credentials;
    @Mock private RequestLeaseService requestLeases;
    @Mock private DistributedConcurrencyLimiter concurrencyLimiter;
    @Mock private BillingControlService billing;
    @Mock private BillingRecoveryService recovery;
    @Mock private ModelProviderClient providerClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestIds requestIds = new RequestIds();
    private ModelGatewayOrchestrator orchestrator;
    private ResolvedModelConfig model;
    private RequestLease lease;
    private ConcurrencyPermit permit;
    private GatewayRequestContext context;

    @BeforeEach
    void setUp() {
        model = model();
        lease = new RequestLease("request-key", "token", "mgw-request");
        permit = new ConcurrencyPermit("user-key", "model-key", "permit");
        context = new GatewayRequestContext(42L, "session", "device", "DESKTOP", "trace", "client-12345678");
        orchestrator = new ModelGatewayOrchestrator(
                new ChatRequestValidator(), modelCache, credentials, new QuotaCalculator(), requestIds,
                requestLeases, concurrencyLimiter, billing, recovery, providerClient,
                new ProviderUsageParser(), objectMapper, properties()
        );
        when(requestLeases.acquire(context)).thenReturn(Mono.just(lease));
        when(requestLeases.release(any())).thenReturn(Mono.empty());
        when(modelCache.resolve("test-model")).thenReturn(Mono.just(model));
        when(credentials.resolve("TEST_KEY")).thenReturn("secret");
        when(concurrencyLimiter.acquire(42L, "test-model")).thenReturn(Mono.just(permit));
        when(concurrencyLimiter.release(any())).thenReturn(Mono.empty());
        when(billing.reserve(any())).thenReturn(Mono.just(new ReservationResponse(
                "reservation", "mgw-request", 42L, "test-model", "pricing-v1",
                ReservationStatus.ACTIVE, amount("1"), null, amount("9"), Instant.now().plusSeconds(60), false
        )));
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
                        """), ProviderProtocol.OPENAI_COMPATIBLE)
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
                        """), ProviderProtocol.OPENAI_COMPATIBLE))
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
                        """), ProviderProtocol.OPENAI_COMPATIBLE)
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
    void neverCallsProviderForIdempotentReservationReplay() throws Exception {
        when(billing.reserve(any())).thenReturn(Mono.just(new ReservationResponse(
                "reservation", "mgw-request", 42L, "test-model", "pricing-v1",
                ReservationStatus.ACTIVE, amount("1"), null, amount("9"), Instant.now().plusSeconds(60), true
        )));

        StepVerifier.create(orchestrator.invoke(context, objectMapper.readTree("""
                        {"model":"test-model","messages":[]}
                        """), ProviderProtocol.OPENAI_COMPATIBLE))
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
                model.capabilities(), model.quotaRates(), model.publishedAt()
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
                        """), ProviderProtocol.OPENAI_COMPATIBLE))
                .expectNextCount(1)
                .verifyComplete();

        verify(credentials).invalidate("cred_test");
        verify(credentials, times(2)).resolve("cred_test");
        verify(providerClient, times(2)).invoke(any(), any(), any(), anyBoolean(), any());
    }

    private ResolvedModelConfig model() {
        return new ResolvedModelConfig(
                "test-model", "Test", null, "pricing-v1", "provider", "OPENAI_COMPATIBLE",
                "https://api.example.com/v1", "TEST_KEY", "upstream-model",
                new ModelCapabilities(1_000, 100, true, true, true, true),
                new QuotaRates(amount("10"), amount("20"), amount("20"), amount("10"), amount("10"), amount("0.000001")),
                Instant.now()
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
