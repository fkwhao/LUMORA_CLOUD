package com.lumora.cloud.modelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.CatalogContracts.*;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.billing.BillingContracts.SettleRequest;
import com.lumora.cloud.modelgateway.protocol.LumoraProtocolAdapter;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import com.lumora.cloud.modelgateway.billing.QuotaCalculator;
import com.lumora.cloud.modelgateway.domain.model.TokenUsage;
import com.lumora.cloud.modelgateway.recovery.*;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IssueListGatewayReproductionTest {
    private ResolvedModelConfig model(QuotaRates rates) {
        return new ResolvedModelConfig("test-model","Test",null,"pricing-v1","provider","OPENAI_COMPATIBLE",
            "https://test.invalid/v1","TEST_ONLY","test-upstream",
            new ModelCapabilities(128000,4096,true,true,true,true,false),
            "USD",new CostRates(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE),null,rates,null,Instant.now());
    }
    private String convert(String sse) {
        var adapter=new LumoraProtocolAdapter(new ObjectMapper(),new ProviderUsageParser());
        return adapter.streamingResponse(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(sse.getBytes(StandardCharsets.UTF_8))),
            model(new QuotaRates(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO)),
            ProviderProtocol.OPENAI_COMPATIBLE,"issue-lm008")
          .map(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);DataBufferUtils.release(buffer);return new String(bytes,StandardCharsets.UTF_8);})
          .collectList().map(parts->String.join("",parts)).block(Duration.ofSeconds(5));
    }
    @Test void lm008PartialEofMustNotBecomeSuccess() {
        Throwable error=catchThrowable(()->convert("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}\n\n"));
        assertThat(error).isInstanceOf(com.lumora.cloud.modelgateway.error.ApiException.class);
        assertThat(((com.lumora.cloud.modelgateway.error.ApiException)error).getCode()).isEqualTo("UPSTREAM_STREAM_INCOMPLETE");
        System.out.println("FIX LM-008 partial EOF rejected");
    }
    @Test void lm008ErrorObjectMustNotBecomeSuccess() {
        Throwable error=catchThrowable(()->convert("data: {\"error\":{\"message\":\"provider rejected\",\"type\":\"server_error\"}}\n\n"));
        assertThat(error).isInstanceOf(com.lumora.cloud.modelgateway.error.ApiException.class);
        assertThat(((com.lumora.cloud.modelgateway.error.ApiException)error).getCode()).isEqualTo("UPSTREAM_STREAM_FAILED");
        System.out.println("FIX LM-008 upstream error rejected");
    }
    @Test void lm008AcceptsFinishReasonWithoutDoneMarker() {
        String result=convert("data: {\"choices\":[{\"delta\":{\"content\":\"complete\"},\"finish_reason\":\"stop\"}]}\n\n");
        assertThat(result).contains("\"type\":\"completed\"","complete","[DONE]");
    }

    @Test void lm013ZeroActualFeeMustSettle() {
        var calculator=new QuotaCalculator();
        var model=model(new QuotaRates(BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO));
        var snapshot=calculator.snapshot(model,Instant.now());
        System.out.println("REPRO LM-013 reserved="+calculator.maximum(model,100,snapshot)+" actual usage=100 cached input,0 output");
        Throwable error=catchThrowable(()->calculator.actual(model,new TokenUsage(0,0,0,100,0),snapshot));
        System.out.println("REPRO LM-013 actualFeeError="+(error==null?"NONE":error.getMessage()));
        assertThat(error).as("legal all-cached zero-fee usage must finish normally").isNull();
        assertThat(calculator.actual(model,new TokenUsage(0,0,0,100,0),snapshot)).isEqualByComparingTo("0.000000");
    }
    @Test void dec001ShortRequestAdmissionUsesRequestEstimate() throws Exception {
        var calculator=new QuotaCalculator();
        var model=model(new QuotaRates(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO));
        var snapshot=calculator.snapshot(model,Instant.now());
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(
            "{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":100}");
        var request=new com.lumora.cloud.modelgateway.domain.model.ValidatedChatRequest(
            "test-model",com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol.OPENAI_COMPATIBLE,false,100,body);
        var maximum=calculator.maximum(model,request,snapshot);
        var actual=calculator.actual(model,new TokenUsage(10,10,0,0,0),snapshot);
        var remaining=new BigDecimal("0.001");
        System.out.println("REPRO DEC-001 maximum="+maximum+" actual="+actual+" remaining="+remaining);
        assertThat(actual).isLessThan(remaining);
        assertThat(maximum).isLessThan(remaining).isGreaterThan(actual);
    }
    private com.lumora.cloud.modelgateway.domain.model.ValidatedChatRequest request(String json) throws Exception {
        return new com.lumora.cloud.modelgateway.validation.ChatRequestValidator().parse(new ObjectMapper().readTree(json),
                com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol.OPENAI_COMPATIBLE);
    }
    @Test void dec001HistoryToolsUnicodeAndOutputLimitContributeToEstimate() throws Exception {
        var calculator = new QuotaCalculator();
        var model = model(new QuotaRates(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO));
        var snapshot = calculator.snapshot(model, Instant.now());
        var shortRequest = request("{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":10}");
        var longRequest = request("{\"model\":\"test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\",\"tool_calls\":[{\"name\":\"search\",\"arguments\":\"history\"}]}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"search\",\"parameters\":{\"type\":\"object\"}}}],\"max_tokens\":100}");
        assertThat(calculator.maximum(model, longRequest, snapshot)).isGreaterThan(calculator.maximum(model, shortRequest, snapshot));
        assertThat(calculator.estimatedInputTokens(model, longRequest.originalBody()))
                .isGreaterThan(longRequest.originalBody().toString().getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void dec001MultimodalAndHiddenProviderHistoryKeepConservativeBound() throws Exception {
        var calculator = new QuotaCalculator();
        var model = model(new QuotaRates(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO));
        for (String extra : java.util.List.of(
                "\"previous_response_id\":\"resp_123\"",
                "\"messages\":[{\"content\":[{\"type\":\"input_image\",\"image_url\":\"https://test.invalid/image.png\"}]}]",
                "\"tools\":[{\"type\":\"web_search\"}]")) {
            var body = new ObjectMapper().readTree("{\"model\":\"test-model\"," + extra + "}");
            assertThat(calculator.estimatedInputTokens(model, body)).isEqualTo(128000);
        }
    }
    @Test void lm006UnavailableDurableStorageFailsExplicitlyBeforeAccounting() {
        var client=mock(BillingClient.class);
        var store=mock(RecoveryStore.class);
        var properties=mock(ModelGatewayProperties.class,RETURNS_DEEP_STUBS);
        when(properties.recovery().scanInterval()).thenReturn(Duration.ofSeconds(10));
        when(store.schedule(any(),any())).thenReturn(Mono.error(new IllegalStateException("injected recovery persistence failure")));
        when(store.due(any())).thenReturn(Flux.empty());
        when(client.settle(anyString(),any())).thenThrow(new IllegalStateException("injected billing outage"));
        var service=new BillingRecoveryService(client,store,properties);
        assertThatThrownBy(() -> service.settle("issue-lm006",
            new SettleRequest("usage-lm006","pricing-v1",100,20,0,0,0,new BigDecimal("0.01"),Instant.now()))
            .block(Duration.ofSeconds(5))).hasMessageContaining("injected recovery persistence failure");
        verify(store).schedule(any(),any());
        verifyNoInteractions(client);
    }
}
