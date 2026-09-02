package com.lumora.cloud.modelgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.CatalogContracts.CostRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.modelgateway.domain.GatewayProtocol;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRequestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatRequestValidator validator = new ChatRequestValidator();

    @Test
    void replacesLogicalModelCapsOutputAndForcesStreamUsage() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {"model":"LUMORA-GPT","stream":true,"max_tokens":9999,"stream_options":false,"messages":[]}
                """), GatewayProtocol.OPENAI_COMPATIBLE);
        var upstream = validator.upstreamBody(parsed, model("OPENAI_COMPATIBLE", true, true, true, true));

        assertThat(upstream.get("model").textValue()).isEqualTo("provider-model");
        assertThat(upstream.get("max_tokens").longValue()).isEqualTo(512);
        assertThat(upstream.path("stream_options").path("include_usage").booleanValue()).isTrue();
    }

    @Test
    void rejectsUnsupportedToolsBeforeCallingProvider() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {"model":"lumora-gpt","tools":[{"type":"function"}],"messages":[]}
                """), GatewayProtocol.OPENAI_COMPATIBLE);

        assertThatThrownBy(() -> validator.upstreamBody(
                        parsed, model("OPENAI_COMPATIBLE", true, false, true, true)
                ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("MODEL_TOOLS_UNSUPPORTED");
    }

    @Test
    void preparesAnthropicMessagesRequest() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {
                  "model":"lumora-claude",
                  "stream":true,
                  "max_tokens":9999,
                  "metadata":{"user_id":"must-not-leak"},
                  "system":"Be concise",
                  "messages":[{"role":"user","content":"hello"}]
                }
                """), GatewayProtocol.ANTHROPIC);

        var upstream = validator.upstreamBody(
                parsed, model("ANTHROPIC", true, true, true, true)
        );

        assertThat(upstream.path("model").textValue()).isEqualTo("provider-model");
        assertThat(upstream.path("max_tokens").longValue()).isEqualTo(512);
        assertThat(upstream.path("stream").booleanValue()).isTrue();
        assertThat(upstream.path("system").textValue()).isEqualTo("Be concise");
        assertThat(upstream.has("metadata")).isFalse();
    }

    @Test
    void preparesResponsesRequest() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {
                  "model":"lumora-gpt",
                  "stream":true,
                  "max_output_tokens":9999,
                  "store":true,
                  "safety_identifier":"must-not-leak",
                  "input":[{"role":"user","content":"hello"}]
                }
                """), GatewayProtocol.RESPONSES);

        var upstream = validator.upstreamBody(
                parsed, model("RESPONSES", true, true, true, true)
        );

        assertThat(upstream.path("model").textValue()).isEqualTo("provider-model");
        assertThat(upstream.path("max_output_tokens").longValue()).isEqualTo(512);
        assertThat(upstream.path("stream").booleanValue()).isTrue();
        assertThat(upstream.path("store").booleanValue()).isFalse();
        assertThat(upstream.has("safety_identifier")).isFalse();
    }

    @Test
    void rejectsCallingModelThroughWrongProtocolEndpoint() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {"model":"lumora-gpt","messages":[]}
                """), GatewayProtocol.OPENAI_COMPATIBLE);

        assertThatThrownBy(() -> validator.upstreamBody(
                        parsed, model("ANTHROPIC", true, true, true, true)
                ))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("MODEL_PROTOCOL_MISMATCH");
    }

    @Test
    void validatesLumoraInternalProtocolVersionAndOutputLimit() throws Exception {
        var parsed = validator.parse(objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-gpt",
                  "stream":true,
                  "messages":[],
                  "generation":{"maxOutputTokens":256}
                }
                """), GatewayProtocol.LUMORA_INTERNAL);

        assertThat(parsed.protocol()).isEqualTo(GatewayProtocol.LUMORA_INTERNAL);
        assertThat(parsed.requestedMaxOutputTokens()).isEqualTo(256);
    }

    private ResolvedModelConfig model(
            String protocol,
            boolean reasoning,
            boolean tools,
            boolean vision,
            boolean json
    ) {
        BigDecimal one = BigDecimal.ONE;
        return new ResolvedModelConfig(
                "lumora-gpt", "Lumora GPT", null, "pricing-v1", "provider", protocol,
                "https://api.example.com/v1", "TEST_KEY", "provider-model",
                new ModelCapabilities(8_192, 512, reasoning, tools, vision, json, false),
                "USD", new CostRates(one, one, one, one), null,
                new QuotaRates(one, one, one, one, one), null, Instant.now()
        );
    }
}
