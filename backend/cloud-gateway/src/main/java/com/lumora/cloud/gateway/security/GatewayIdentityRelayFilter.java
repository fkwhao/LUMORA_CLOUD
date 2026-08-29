package com.lumora.cloud.gateway.security;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.gateway.config.GatewayAuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GatewayIdentityRelayFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;
    private final GatewayAuthProperties properties;
    private final GatewayErrorResponseWriter errorWriter;

    public GatewayIdentityRelayFilter(
            ReactiveStringRedisTemplate redis,
            GatewayAuthProperties properties,
            GatewayErrorResponseWriter errorWriter
    ) {
        this.redis = redis;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(AuthHeaders.REQUEST_ID, requestId);

        Mono<Optional<Authentication>> currentAuthentication = ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return currentAuthentication.flatMap(authentication -> {
            if (authentication.isPresent() && authentication.get().getPrincipal() instanceof Jwt jwt) {
                return relayAuthenticated(exchange, chain, jwt, requestId);
            }
            return chain.filter(withTrustedHeaders(exchange, null, requestId));
        });
    }

    private Mono<Void> relayAuthenticated(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Jwt jwt,
            String requestId
    ) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Access Token 缺少会话信息");
        }
        return redis.hasKey(AuthHeaders.REVOKED_SESSION_PREFIX + sessionId)
                .flatMap(revoked -> revoked
                        ? errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录会话已经退出")
                        : chain.filter(withTrustedHeaders(exchange, jwt, requestId)))
                .onErrorResume(exception -> errorWriter.write(
                        exchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AUTH_STATE_UNAVAILABLE",
                        "登录状态服务暂时不可用"
                ));
    }

    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, Jwt jwt, String requestId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    AuthHeaders.CLIENT_FORBIDDEN_HEADERS.forEach(headers::remove);
                    headers.set(AuthHeaders.REQUEST_ID, requestId);
                    headers.set(AuthHeaders.SERVICE_ID, "lumora-cloud-gateway");
                    headers.set(AuthHeaders.INTERNAL_TOKEN, properties.security().internalToken());
                    if (jwt != null) {
                        headers.set(AuthHeaders.USER_ID, jwt.getSubject());
                        headers.set(AuthHeaders.SESSION_ID, jwt.getClaimAsString("sid"));
                        headers.set(AuthHeaders.DEVICE_ID, jwt.getClaimAsString("device_id"));
                        headers.set(AuthHeaders.CLIENT_TYPE, jwt.getClaimAsString("client_type"));
                        List<String> roles = jwt.getClaimAsStringList("roles");
                        headers.set(AuthHeaders.ROLES, roles == null ? "" : String.join(",", roles));
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
