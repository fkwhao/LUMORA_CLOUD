package com.lumora.cloud.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityConfigurationTest {

    private final GatewaySecurityConfiguration configuration = new GatewaySecurityConfiguration();

    @Test
    void acceptsAccessTokenFromAnthropicApiKeyHeaderOnlyOnMessagesEndpoint() {
        var messages = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/app/model/v1/messages")
                .header("x-api-key", "cloud-access-token"));

        var authentication = configuration.modelAccessTokenConverter().convert(messages).block();

        assertThat(authentication).isInstanceOf(BearerTokenAuthenticationToken.class);
        assertThat(authentication.getCredentials()).isEqualTo("cloud-access-token");
    }

    @Test
    void doesNotTreatApiKeyHeaderAsLoginOutsideAnthropicMessagesEndpoint() {
        var other = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/app/model/v1/responses")
                .header("x-api-key", "cloud-access-token"));

        assertThat(configuration.modelAccessTokenConverter().convert(other).block()).isNull();
    }
}
