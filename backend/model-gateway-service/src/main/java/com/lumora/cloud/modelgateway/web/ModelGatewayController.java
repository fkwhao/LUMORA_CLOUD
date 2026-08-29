package com.lumora.cloud.modelgateway.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import com.lumora.cloud.modelgateway.service.ModelGatewayOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/app/model/v1")
public class ModelGatewayController {

    private final ModelGatewayAccess access;
    private final ModelGatewayOrchestrator orchestrator;

    public ModelGatewayController(ModelGatewayAccess access, ModelGatewayOrchestrator orchestrator) {
        this.access = access;
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> chatCompletions(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, ProviderProtocol.OPENAI_COMPATIBLE);
    }

    @PostMapping(value = "/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> responses(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, ProviderProtocol.RESPONSES);
    }

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> messages(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, ProviderProtocol.ANTHROPIC);
    }

    private Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> invoke(
            ServerWebExchange exchange,
            Mono<JsonNode> body,
            ProviderProtocol protocol
    ) {
        GatewayRequestContext context = access.requireUser(exchange.getRequest().getHeaders());
        return body.switchIfEmpty(Mono.error(new com.lumora.cloud.modelgateway.error.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST_BODY",
                        "请求体不能为空"
                )))
                .flatMap(request -> orchestrator.invoke(context, request, protocol))
                .map(response -> ResponseEntity.status(response.status())
                        .headers(response.headers())
                        .body(response.body()));
    }
}
