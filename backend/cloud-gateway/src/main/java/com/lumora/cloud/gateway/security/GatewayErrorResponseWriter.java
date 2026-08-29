package com.lumora.cloud.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.common.ApiError;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
public class GatewayErrorResponseWriter implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态无效或已过期");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return write(exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行该操作");
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        String requestId = exchange.getRequest().getHeaders().getFirst(AuthHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
        exchange.getResponse().getHeaders().set(AuthHeaders.REQUEST_ID, requestId);
        try {
            byte[] body = objectMapper.writeValueAsBytes(new ApiError(code, message, requestId, Instant.now()));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return exchange.getResponse().setComplete();
        }
    }
}
