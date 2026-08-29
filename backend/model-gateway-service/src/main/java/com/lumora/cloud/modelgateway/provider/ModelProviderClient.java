package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.config.ModelGatewayProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Component
public class ModelProviderClient {

    static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ModelGatewayProperties properties;

    public ModelProviderClient(
            @Qualifier("providerWebClient") WebClient webClient,
            ModelGatewayProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Mono<ProviderCall> invoke(
            ResolvedModelConfig model,
            String credential,
            ObjectNode body,
            boolean stream,
            String traceId
    ) {
        return Mono.defer(() -> {
            long deadlineNanos = System.nanoTime() + properties.provider().maxCallDuration().toNanos();
            ProviderProtocol protocol = ProviderProtocol.parse(model.protocolType());
            URI endpoint = URI.create(model.baseUrl() + endpointPath(protocol));
            WebClient.RequestBodySpec request = webClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT,
                            stream ? MediaType.TEXT_EVENT_STREAM_VALUE : MediaType.APPLICATION_JSON_VALUE)
                    .header(AuthHeaders.REQUEST_ID, traceId);
            authenticate(request, protocol, credential);
            return request.bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(byte[].class)
                                    .defaultIfEmpty(new byte[0])
                                    .map(ignored -> new ProviderHttpException(response.statusCode())))
                    .toEntityFlux(org.springframework.core.io.buffer.DataBuffer.class)
                    .map(entity -> new ProviderCall(
                            entity.getStatusCode(),
                            sanitize(entity.getHeaders()),
                            withDeadline(entity.getBody(), deadlineNanos)
                    ))
                    .switchIfEmpty(Mono.error(new IllegalStateException("Model provider returned no HTTP response")))
                    .timeout(remaining(deadlineNanos));
        });
    }

    static String endpointPath(ProviderProtocol protocol) {
        return switch (protocol) {
            case ANTHROPIC -> "/messages";
            case OPENAI_COMPATIBLE -> "/chat/completions";
            case RESPONSES -> "/responses";
        };
    }

    private void authenticate(
            WebClient.RequestBodySpec request,
            ProviderProtocol protocol,
            String credential
    ) {
        if (protocol == ProviderProtocol.ANTHROPIC) {
            request.header("x-api-key", credential)
                    .header("anthropic-version", ANTHROPIC_VERSION);
        } else {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + credential);
        }
    }

    private <T> Flux<T> withDeadline(Flux<T> source, long deadlineNanos) {
        return source.timeout(
                Mono.delay(remaining(deadlineNanos)),
                ignored -> Mono.delay(remaining(deadlineNanos))
        );
    }

    private Duration remaining(long deadlineNanos) {
        return Duration.ofNanos(Math.max(1L, deadlineNanos - System.nanoTime()));
    }

    private HttpHeaders sanitize(HttpHeaders upstream) {
        HttpHeaders result = new HttpHeaders();
        MediaType contentType = upstream.getContentType();
        if (contentType != null) {
            result.setContentType(contentType);
        }
        String requestId = firstHeader(upstream, "x-request-id", "request-id");
        if (requestId != null && !requestId.isBlank()) {
            result.set("X-Upstream-Request-Id", requestId);
        }
        result.setCacheControl("no-store");
        return result;
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
