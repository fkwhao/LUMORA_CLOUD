package com.lumora.cloud.modelgateway.controller.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import com.lumora.cloud.modelgateway.diagnostics.GatewayDiagnosticsStore;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import com.lumora.cloud.modelgateway.service.IModelGatewayService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/app/model/v1")
public class ModelGatewayController {

    private final ModelGatewayAccess access;
    private final IModelGatewayService modelGatewayService;
    private final GatewayDiagnosticsStore diagnostics;

    public ModelGatewayController(
            ModelGatewayAccess access,
            IModelGatewayService modelGatewayService,
            GatewayDiagnosticsStore diagnostics
    ) {
        this.access = access;
        this.modelGatewayService = modelGatewayService;
        this.diagnostics = diagnostics;
    }

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> chatCompletions(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, GatewayProtocol.OPENAI_COMPATIBLE);
    }

    @PostMapping(value = "/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> responses(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, GatewayProtocol.RESPONSES);
    }

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> messages(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, GatewayProtocol.ANTHROPIC);
    }

    @PostMapping(value = "/invoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> invokeLumora(
            ServerWebExchange exchange,
            @RequestBody Mono<JsonNode> body
    ) {
        return invoke(exchange, body, GatewayProtocol.LUMORA_INTERNAL);
    }

    private Mono<ResponseEntity<Flux<org.springframework.core.io.buffer.DataBuffer>>> invoke(
            ServerWebExchange exchange,
            Mono<JsonNode> body,
            GatewayProtocol protocol
    ) {
        GatewayRequestContext context = access.requireUser(exchange.getRequest().getHeaders());
        return body.switchIfEmpty(Mono.error(new com.lumora.cloud.modelgateway.error.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST_BODY",
                        "请求体不能为空"
                )))
                .flatMap(request -> {
                    String modelCode = request.path("model").asText("");
                    boolean stream = request.path("stream").asBoolean(false);
                    return diagnostics.started(context, modelCode, protocol.name(), stream)
                            .flatMap(diagnostic -> modelGatewayService.invoke(context, request, protocol)
                                    .map(response -> {
                                        String providerCode = response.headers().getFirst("X-Lumora-Provider-Code");
                                        String routeId = response.headers().getFirst("X-Lumora-Route-Id");
                                        String routeName = response.headers().getFirst("X-Lumora-Route-Name");
                                        AtomicBoolean completed = new AtomicBoolean();
                                        Flux<org.springframework.core.io.buffer.DataBuffer> tracked = response.body()
                                                .doOnComplete(() -> completeOnce(
                                                        completed,
                                                        diagnostics.succeeded(
                                                                diagnostic, providerCode, routeId, routeName,
                                                                response.status().value()
                                                        )
                                                ))
                                                .doOnError(error -> completeOnce(
                                                        completed,
                                                        diagnostics.failed(
                                                                diagnostic, providerCode, routeId, routeName,
                                                                response.status().value(), errorCode(error)
                                                        )
                                                ))
                                                .doOnCancel(() -> completeOnce(
                                                        completed, diagnostics.canceled(
                                                                diagnostic, providerCode, routeId, routeName
                                                        )
                                                ));
                                        return ResponseEntity.status(response.status())
                                                .headers(response.headers())
                                                .body(tracked);
                                    })
                                    .onErrorResume(error -> diagnostics.failed(
                                                    diagnostic, "", "", "", null, errorCode(error)
                                            )
                                            .then(Mono.error(error))));
                });
    }

    private void completeOnce(AtomicBoolean completed, Mono<Void> operation) {
        if (completed.compareAndSet(false, true)) {
            operation.subscribe();
        }
    }

    private String errorCode(Throwable error) {
        return error instanceof ApiException apiException
                ? apiException.getCode()
                : error.getClass().getSimpleName();
    }
}
