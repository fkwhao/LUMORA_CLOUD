package com.lumora.cloud.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GatewayIdentityRelayFilterTest {

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final GatewayIdentityRelayFilter filter = new GatewayIdentityRelayFilter(
            redis,
            properties(),
            new GatewayErrorResponseWriter(new ObjectMapper())
    );

    @Test
    void removesForgedIdentityHeadersFromAnonymousRequests() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/app/auth/login")
                .header(AuthHeaders.USER_ID, "forged-user")
                .header(AuthHeaders.ROLES, "ADMIN")
                .header(AuthHeaders.INTERNAL_TOKEN, "forged-token")
                .header(AuthHeaders.REQUEST_ID, "forged-request")
                .build());
        AtomicReference<ServerWebExchange> relayed = new AtomicReference<>();

        filter.filter(exchange, capture(relayed)).block();

        HttpHeaders headers = relayed.get().getRequest().getHeaders();
        assertThat(headers.getFirst(AuthHeaders.USER_ID)).isNull();
        assertThat(headers.getFirst(AuthHeaders.ROLES)).isNull();
        assertThat(headers.getFirst(AuthHeaders.INTERNAL_TOKEN)).isEqualTo("internal-test-token");
        assertThat(headers.getFirst(AuthHeaders.SERVICE_ID)).isEqualTo("lumora-cloud-gateway");
        assertThat(headers.getFirst(AuthHeaders.REQUEST_ID)).isNotBlank().isNotEqualTo("forged-request");
        verifyNoInteractions(redis);
    }

    @Test
    void injectsOnlyVerifiedJwtIdentityIntoDownstreamRequest() {
        when(redis.hasKey(AuthHeaders.REVOKED_SESSION_PREFIX + "session-7")).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/app/users/me")
                .header(AuthHeaders.USER_ID, "forged-user")
                .build());
        AtomicReference<ServerWebExchange> relayed = new AtomicReference<>();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        filter.filter(exchange, capture(relayed))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        HttpHeaders headers = relayed.get().getRequest().getHeaders();
        assertThat(headers.getFirst(AuthHeaders.USER_ID)).isEqualTo("7");
        assertThat(headers.getFirst(AuthHeaders.SESSION_ID)).isEqualTo("session-7");
        assertThat(headers.getFirst(AuthHeaders.DEVICE_ID)).isEqualTo("device-7");
        assertThat(headers.getFirst(AuthHeaders.CLIENT_TYPE)).isEqualTo("WEB");
        assertThat(headers.getFirst(AuthHeaders.ROLES)).isEqualTo("USER,ADMIN");
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> exchange) {
        return current -> {
            exchange.set(current);
            return Mono.empty();
        };
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("sid", "session-7")
                .claim("device_id", "device-7")
                .claim("client_type", "WEB")
                .claim("roles", List.of("USER", "ADMIN"))
                .build();
    }

    private GatewayAuthProperties properties() {
        return new GatewayAuthProperties(
                new GatewayAuthProperties.Auth(new GatewayAuthProperties.Jwt(
                        "unit-test-secret-with-at-least-thirty-two-characters",
                        "lumora-test",
                        "lumora-test-api"
                )),
                new GatewayAuthProperties.Security("internal-test-token")
        );
    }
}
