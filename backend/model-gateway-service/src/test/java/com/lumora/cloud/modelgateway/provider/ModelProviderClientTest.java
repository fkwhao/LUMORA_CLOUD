package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesAnthropicMessagesEndpointAndHeaders() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ModelProviderClient client = client(captured);

        client.invoke(model("ANTHROPIC"), "anthropic-secret", objectMapper.createObjectNode(), false, "trace")
                .flatMap(call -> call.body().then())
                .block();

        assertThat(captured.get().url().toString()).isEqualTo("https://api.example.com/v1/messages");
        assertThat(captured.get().headers().getFirst("x-api-key")).isEqualTo("anthropic-secret");
        assertThat(captured.get().headers().getFirst("anthropic-version"))
                .isEqualTo(ModelProviderClient.ANTHROPIC_VERSION);
        assertThat(captured.get().headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void usesResponsesEndpointAndBearerAuthentication() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ModelProviderClient client = client(captured);

        client.invoke(model("RESPONSES"), "openai-secret", objectMapper.createObjectNode(), true, "trace")
                .flatMap(call -> call.body().then())
                .block();

        assertThat(captured.get().url().toString()).isEqualTo("https://api.example.com/v1/responses");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer openai-secret");
        assertThat(captured.get().headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void mapsEveryProtocolToItsNativeEndpoint() {
        assertThat(ModelProviderClient.endpointPath(ProviderProtocol.ANTHROPIC)).isEqualTo("/messages");
        assertThat(ModelProviderClient.endpointPath(ProviderProtocol.OPENAI_COMPATIBLE))
                .isEqualTo("/chat/completions");
        assertThat(ModelProviderClient.endpointPath(ProviderProtocol.RESPONSES)).isEqualTo("/responses");
    }

    private ModelProviderClient client(AtomicReference<ClientRequest> captured) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{}")
                            .build());
                })
                .build();
        return new ModelProviderClient(webClient, properties());
    }

    private ResolvedModelConfig model(String protocol) {
        BigDecimal one = BigDecimal.ONE;
        return new ResolvedModelConfig(
                "test", "Test", null, "pricing", "provider", protocol,
                "https://api.example.com/v1", "credential", "upstream-model",
                new ModelCapabilities(8_192, 512, true, true, true, true),
                new QuotaRates(one, one, one, one, one, one), Instant.now()
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
}
