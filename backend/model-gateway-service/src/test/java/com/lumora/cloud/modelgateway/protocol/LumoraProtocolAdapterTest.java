package com.lumora.cloud.modelgateway.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.CatalogContracts.CostRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ModelCapabilities;
import com.lumora.cloud.api.catalog.CatalogContracts.QuotaRates;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LumoraProtocolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LumoraProtocolAdapter adapter = new LumoraProtocolAdapter(
            objectMapper, new ProviderUsageParser()
    );

    @Test
    void translatesUnifiedMessagesAndToolsToAnthropic() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-claude",
                  "stream":true,
                  "messages":[
                    {"role":"system","content":[{"type":"text","text":"Be concise"}]},
                    {"role":"user","content":[{"type":"text","text":"hello"}]},
                    {"role":"assistant","content":[],"toolCalls":[{"id":"call-1","name":"read_file","arguments":"{\\"path\\":\\"a.txt\\"}"}]},
                    {"role":"tool","toolCallId":"call-1","content":[{"type":"text","text":"file"}]}
                  ],
                  "tools":[{"name":"read_file","description":"Read","inputSchema":{"type":"object"}}],
                  "generation":{"maxOutputTokens":9999,"reasoningEffort":"medium"}
                }
                """);

        var body = adapter.upstreamBody(request, model("ANTHROPIC"),
                ProviderProtocol.ANTHROPIC, true, 9999);

        assertThat(body.path("model").asText()).isEqualTo("upstream-model");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(4096);
        assertThat(body.path("system").asText()).isEqualTo("Be concise");
        assertThat(body.path("messages").toString()).contains("tool_use", "tool_result", "read_file");
        assertThat(body.path("tools").path(0).path("input_schema").path("type").asText())
                .isEqualTo("object");
        assertThat(body.path("thinking").path("type").asText()).isEqualTo("enabled");
    }

    @Test
    void translatesUnifiedMessagesToOpenAiWithoutExposingTheLogicalModelUpstream() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-openai",
                  "stream":true,
                  "messages":[{"role":"user","content":[
                    {"type":"text","text":"describe"},
                    {"type":"image","url":"data:image/png;base64,AA=="}
                  ]}],
                  "tools":[{"name":"read_file","description":"Read","inputSchema":{"type":"object"}}],
                  "generation":{"maxOutputTokens":1024}
                }
                """);

        var body = adapter.upstreamBody(request, model("OPENAI_COMPATIBLE"),
                ProviderProtocol.OPENAI_COMPATIBLE, true, 1024);

        assertThat(body.path("model").asText()).isEqualTo("upstream-model");
        assertThat(body.path("messages").path(0).path("content").toString())
                .contains("image_url", "data:image/png");
        assertThat(body.path("tools").path(0).path("function").path("name").asText())
                .isEqualTo("read_file");
        assertThat(body.path("stream_options").path("include_usage").asBoolean()).isTrue();
    }

    @Test
    void translatesUnifiedToolContinuationToResponses() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-responses",
                  "stream":false,
                  "messages":[
                    {"role":"assistant","content":[],"toolCalls":[{"id":"call-1","name":"read_file","arguments":"{}"}]},
                    {"role":"tool","toolCallId":"call-1","content":[{"type":"text","text":"file"}]}
                  ],
                  "tools":[],
                  "generation":{"maxOutputTokens":2048,"reasoningEffort":"high"}
                }
                """);

        var body = adapter.upstreamBody(request, model("RESPONSES"),
                ProviderProtocol.RESPONSES, false, 2048);

        assertThat(body.path("model").asText()).isEqualTo("upstream-model");
        assertThat(body.path("input").toString())
                .contains("function_call", "function_call_output", "call-1");
        assertThat(body.path("reasoning").path("effort").asText()).isEqualTo("high");
        assertThat(body.path("store").asBoolean()).isFalse();
    }

    @Test
    void mapsUnifiedResponseSchemaToProviderStructuredOutput() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-test",
                  "stream":false,
                  "messages":[{"role":"user","content":[{"type":"text","text":"extract"}]}],
                  "tools":[],
                  "generation":{"responseSchema":{"type":"object","properties":{"candidates":{"type":"array"}},"required":["candidates"]}}
                }
                """);

        var openAi = adapter.upstreamBody(request, model("OPENAI_COMPATIBLE"),
                ProviderProtocol.OPENAI_COMPATIBLE, false, 2048);
        var responses = adapter.upstreamBody(request, model("RESPONSES"),
                ProviderProtocol.RESPONSES, false, 2048);
        var anthropic = adapter.upstreamBody(request, model("ANTHROPIC"),
                ProviderProtocol.ANTHROPIC, false, 2048);

        assertThat(openAi.path("response_format").path("type").asText())
                .isEqualTo("json_object");
        assertThat(responses.path("text").path("format").path("type").asText())
                .isEqualTo("json_schema");
        assertThat(responses.path("text").path("format").path("schema")
                .path("required").path(0).asText()).isEqualTo("candidates");
        assertThat(anthropic.path("output_config").path("format").path("type").asText())
                .isEqualTo("json_schema");
        assertThat(anthropic.path("output_config").path("format").path("schema")
                .path("type").asText()).isEqualTo("object");
    }

    @Test
    void leavesResponseSchemaAsBestEffortWhenModelDoesNotSupportJson() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-test",
                  "stream":false,
                  "messages":[{"role":"user","content":[{"type":"text","text":"extract"}]}],
                  "tools":[],
                  "generation":{"responseSchema":{"type":"object"}}
                }
                """);

        var body = adapter.upstreamBody(request, model("RESPONSES", false, false),
                ProviderProtocol.RESPONSES, false, 2048);

        assertThat(body.has("text")).isFalse();
    }

    @Test
    void rejectsNonObjectUnifiedResponseSchema() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-test",
                  "stream":false,
                  "messages":[{"role":"user","content":[{"type":"text","text":"extract"}]}],
                  "tools":[],
                  "generation":{"responseSchema":"invalid"}
                }
                """);

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> adapter.upstreamBody(request, model("RESPONSES"),
                        ProviderProtocol.RESPONSES, false, 2048))
                .satisfies(error -> assertThat(error.getCode())
                        .isEqualTo("LUMORA_RESPONSE_SCHEMA_INVALID"));
    }

    @Test
    void addsHostedWebSearchToolForResponses() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-responses",
                  "stream":true,
                  "messages":[{"role":"user","content":[{"type":"text","text":"latest news"}]}],
                  "tools":[],
                  "features":{"webSearch":true}
                }
                """);

        var body = adapter.upstreamBody(request, model("RESPONSES", true),
                ProviderProtocol.RESPONSES, true, 2048);

        assertThat(body.path("tools").path(0).path("type").asText()).isEqualTo("web_search");
        assertThat(body.path("include").path(0).asText())
                .isEqualTo("web_search_call.action.sources");
    }

    @Test
    void addsHostedWebSearchToolForAnthropic() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree("""
                {
                  "protocolVersion":"1",
                  "model":"lumora-anthropic",
                  "stream":true,
                  "messages":[{"role":"user","content":[{"type":"text","text":"latest news"}]}],
                  "tools":[],
                  "features":{"webSearch":true}
                }
                """);

        var body = adapter.upstreamBody(request, model("ANTHROPIC", true),
                ProviderProtocol.ANTHROPIC, true, 2048);

        assertThat(body.path("tools").path(0).path("type").asText())
                .isEqualTo("web_search_20250305");
        assertThat(body.path("tools").path(0).path("name").asText()).isEqualTo("web_search");
    }

    @Test
    void normalizesAnthropicBufferedResponse() throws Exception {
        byte[] response = """
                {
                  "model":"claude-test",
                  "content":[
                    {"type":"thinking","thinking":"reason","signature":"signed"},
                    {"type":"text","text":"answer"},
                    {"type":"tool_use","id":"call-1","name":"read_file","input":{"path":"a.txt"}}
                  ],
                  "usage":{"input_tokens":10,"output_tokens":6}
                }
                """.getBytes(StandardCharsets.UTF_8);

        var normalized = objectMapper.readTree(adapter.bufferedResponse(
                response, model("ANTHROPIC"), ProviderProtocol.ANTHROPIC, "trace-1"
        ));

        assertThat(normalized.path("protocolVersion").asText()).isEqualTo("1");
        assertThat(normalized.path("result").path("content").asText()).isEqualTo("answer");
        assertThat(normalized.path("result").path("reasoning").asText()).isEqualTo("reason");
        assertThat(normalized.path("result").path("toolCalls").path(0).path("name").asText())
                .isEqualTo("read_file");
        assertThat(normalized.path("result").path("providerState").path("content").toString())
                .contains("signed");
        assertThat(normalized.path("usage").path("promptTokens").asLong()).isEqualTo(10);
    }

    @Test
    void normalizesChunkedResponsesStreamToLumoraEvents() {
        String nativeEvents = """
                data: {"type":"response.created","response":{"model":"gpt-test"}}

                data: {"type":"response.output_text.delta","delta":"hello "}

                data: {"type":"response.output_item.added","output_index":1,"item":{"type":"web_search_call","id":"search-1","action":{"query":"LUMORA"}}}

                data: {"type":"response.web_search_call.searching","item_id":"search-1","action":{"query":"LUMORA"}}

                data: {"type":"response.output_text.delta","delta":"world"}

                data: {"type":"response.completed","response":{"model":"gpt-test","output":[{"type":"web_search_call","id":"search-1","action":{"query":"LUMORA","sources":[{"title":"LUMORA","url":"https://example.com/lumora"}]}}],"usage":{"input_tokens":8,"output_tokens":2}}}

                data: [DONE]

                """;
        byte[] bytes = nativeEvents.getBytes(StandardCharsets.UTF_8);
        Flux<org.springframework.core.io.buffer.DataBuffer> source = Flux.just(
                DefaultDataBufferFactory.sharedInstance.wrap(java.util.Arrays.copyOfRange(bytes, 0, 67)),
                DefaultDataBufferFactory.sharedInstance.wrap(java.util.Arrays.copyOfRange(bytes, 67, bytes.length))
        );

        String normalized = adapter.streamingResponse(
                        source, model("RESPONSES"), ProviderProtocol.RESPONSES, "trace-2"
                )
                .map(buffer -> {
                    byte[] value = new byte[buffer.readableByteCount()];
                    buffer.read(value);
                    DataBufferUtils.release(buffer);
                    return new String(value, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(values -> String.join("", values))
                .block();

        assertThat(normalized).contains("\"type\":\"content_delta\"", "hello ", "world");
        assertThat(normalized).contains(
                "\"type\":\"web_search_started\"",
                "\"type\":\"web_search_progress\"",
                "\"type\":\"web_search_completed\"",
                "https://example.com/lumora"
        );
        assertThat(normalized).contains("\"type\":\"usage\"", "\"type\":\"completed\"", "data: [DONE]");
    }

    @Test
    void normalizesAnthropicHostedSearchStreamToLumoraEvents() {
        String nativeEvents = """
                data: {"type":"message_start","message":{"model":"claude-test","usage":{"input_tokens":8}}}

                data: {"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"search-1","name":"web_search","input":{}}}

                data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\\"query\\\":\\\"LUMORA\\\"}"}}

                data: {"type":"content_block_stop","index":0}

                data: {"type":"content_block_start","index":1,"content_block":{"type":"web_search_tool_result","tool_use_id":"search-1","content":[{"type":"web_search_result","title":"LUMORA","url":"https://example.com/lumora"}]}}

                data: {"type":"message_delta","usage":{"output_tokens":2}}

                data: {"type":"message_stop"}

                """;

        String normalized = adapter.streamingResponse(
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                                nativeEvents.getBytes(StandardCharsets.UTF_8)
                        )),
                        model("ANTHROPIC", true), ProviderProtocol.ANTHROPIC, "trace-3"
                )
                .map(buffer -> {
                    byte[] value = new byte[buffer.readableByteCount()];
                    buffer.read(value);
                    DataBufferUtils.release(buffer);
                    return new String(value, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(values -> String.join("", values))
                .block();

        assertThat(normalized).contains(
                "\"type\":\"web_search_started\"",
                "\"type\":\"web_search_progress\"",
                "\"type\":\"web_search_completed\"",
                "\"query\":\"LUMORA\"",
                "https://example.com/lumora",
                "\"type\":\"completed\""
        );
        assertThat(normalized).doesNotContain("\"type\":\"tool_call_delta\"");
    }

    private ResolvedModelConfig model(String protocol) {
        return model(protocol, false);
    }

    private ResolvedModelConfig model(String protocol, boolean webSearch) {
        return model(protocol, true, webSearch);
    }

    private ResolvedModelConfig model(String protocol, boolean json, boolean webSearch) {
        BigDecimal one = BigDecimal.ONE;
        return new ResolvedModelConfig(
                "lumora-test", "Test", null, "pricing-v1", "provider", protocol,
                "https://api.example.com/v1", "cred", "upstream-model",
                new ModelCapabilities(8_192, 4096, true, true, true, json, webSearch),
                "USD", new CostRates(one, one, one, one), null,
                new QuotaRates(one, one, one, one, one), null, Instant.now()
        );
    }
}
