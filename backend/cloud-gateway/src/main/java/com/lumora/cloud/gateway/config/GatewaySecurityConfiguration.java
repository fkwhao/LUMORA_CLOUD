package com.lumora.cloud.gateway.config;

import com.lumora.cloud.gateway.security.GatewayErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            GatewayErrorResponseWriter errorWriter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/app/auth/register", "/api/app/auth/login", "/api/app/auth/refresh", "/api/app/auth/logout").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/app/**").authenticated()
                        .anyExchange().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorWriter)
                        .accessDeniedHandler(errorWriter))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(errorWriter)
                        .bearerTokenConverter(modelAccessTokenConverter())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    ServerAuthenticationConverter modelAccessTokenConverter() {
        ServerBearerTokenAuthenticationConverter bearer = new ServerBearerTokenAuthenticationConverter();
        return exchange -> bearer.convert(exchange)
                .switchIfEmpty(Mono.defer(() -> {
                    if (!"/api/app/model/v1/messages".equals(exchange.getRequest().getPath().value())) {
                        return Mono.empty();
                    }
                    String token = exchange.getRequest().getHeaders().getFirst("x-api-key");
                    return StringUtils.hasText(token)
                            ? Mono.just(new BearerTokenAuthenticationToken(token.trim()))
                            : Mono.empty();
                }));
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(authority -> (GrantedAuthority) authority)
                    .toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}
