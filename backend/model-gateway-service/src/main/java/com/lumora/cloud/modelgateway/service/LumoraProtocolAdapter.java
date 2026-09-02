package com.lumora.cloud.modelgateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import com.lumora.cloud.modelgateway.error.ApiException;
import com.lumora.cloud.modelgateway.provider.ProviderUsageParser;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LUMORA protocol v1 is the stable contract between the desktop Agent and Cloud.
 * Provider-specific JSON is created and consumed only inside Model Gateway.
 */
@Component
public class LumoraProtocolAdapter {

    public static final String VERSION = "1";
    private static final int MAX_EVENT_LINE_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ProviderUsageParser usageParser;

    public LumoraProtocolAdapter(ObjectMapper objectMapper, ProviderUsageParser usageParser) {
        this.objectMapper = objectMapper;
        this.usageParser = usageParser;
    }

    public ObjectNode upstreamBody(
            ObjectNode request,
            ResolvedModelConfig model,
            ProviderProtocol protocol,
            boolean stream,
            long requestedOutputLimit
    ) {
        validateCapabilities(request, model, protocol);
        long outputLimit = Math.min(model.capabilities().maxOutputTokens(), requestedOutputLimit);
        if (outputLimit == Long.MAX_VALUE) {
            outputLimit = model.capabilities().maxOutputTokens();
        }
        return switch (protocol) {
            case OPENAI_COMPATIBLE -> openAiRequest(request, model, stream, outputLimit);
            case ANTHROPIC -> anthropicRequest(request, model, stream, outputLimit);
            case RESPONSES -> responsesRequest(request, model, stream, outputLimit);
        };
    }

    public byte[] bufferedResponse(
            byte[] upstream,
            ResolvedModelConfig model,
            ProviderProtocol protocol,
            String requestId
    ) {
        try {
            JsonNode payload = objectMapper.readTree(upstream);
            ObjectNode response = envelope(requestId, model.modelCode());
            response.set("result", result(payload, model, protocol));
            TokenUsage usage = usageParser.parse(protocol, payload);
            if (usage != null) {
                response.set("usage", usage(usage));
            }
            return objectMapper.writeValueAsBytes(response);
        } catch (Exception error) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_RESPONSE_INVALID",
                    "模型供应商返回了无法解析的响应", error);
        }
    }

    public Flux<DataBuffer> streamingResponse(
            Flux<DataBuffer> upstream,
            ResolvedModelConfig model,
            ProviderProtocol protocol,
            String requestId
    ) {
        return Flux.defer(() -> {
            StreamTranslator translator = new StreamTranslator(model, protocol, requestId);
            return upstream.concatMap(buffer -> Flux.fromIterable(translator.accept(readAndRelease(buffer))))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(translator.finish())));
        });
    }

    private byte[] readAndRelease(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    private ObjectNode openAiRequest(
            ObjectNode request,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.upstreamModel());
        body.put("stream", stream);
        body.put("max_tokens", outputLimit);
        if (stream) {
            body.putObject("stream_options").put("include_usage", true);
        }
        body.set("messages", openAiMessages(request.path("messages")));
        ArrayNode tools = openAiTools(request.path("tools"));
        if (!tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        String effort = reasoningEffort(request);
        if (!effort.isBlank() && !"none".equals(effort)) {
            body.putObject("reasoning").put("effort", effort);
        }
        return body;
    }

    private ObjectNode responsesRequest(
            ObjectNode request,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.upstreamModel());
        body.put("stream", stream);
        body.put("store", false);
        body.put("max_output_tokens", outputLimit);
        ArrayNode input = body.putArray("input");
        for (JsonNode message : request.path("messages")) {
            String role = message.path("role").asText();
            if ("tool".equals(role)) {
                ObjectNode item = input.addObject();
                item.put("type", "function_call_output");
                item.put("call_id", message.path("toolCallId").asText());
                item.put("output", textContent(message.path("content")));
                continue;
            }
            ObjectNode item = input.addObject();
            item.put("role", role);
            JsonNode content = message.path("content");
            if (hasImage(content)) {
                ArrayNode blocks = item.putArray("content");
                for (JsonNode part : content) {
                    if ("image".equals(part.path("type").asText())) {
                        ObjectNode image = blocks.addObject();
                        image.put("type", "input_image");
                        image.put("image_url", part.path("url").asText());
                    } else if ("text".equals(part.path("type").asText())) {
                        ObjectNode text = blocks.addObject();
                        text.put("type", "input_text");
                        text.put("text", part.path("text").asText());
                    }
                }
            } else {
                item.put("content", textContent(content));
            }
            for (JsonNode call : message.path("toolCalls")) {
                ObjectNode function = input.addObject();
                function.put("type", "function_call");
                function.put("call_id", call.path("id").asText());
                function.put("name", call.path("name").asText());
                function.put("arguments", call.path("arguments").asText("{}"));
            }
        }
        ArrayNode tools = responsesTools(request.path("tools"));
        if (webSearchRequested(request)) {
            tools.addObject().put("type", "web_search");
            body.putArray("include").add("web_search_call.action.sources");
        }
        if (!tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        String effort = reasoningEffort(request);
        if (!effort.isBlank() && !"none".equals(effort)) {
            body.putObject("reasoning").put("effort", effort);
        }
        return body;
    }

    private ObjectNode anthropicRequest(
            ObjectNode request,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.upstreamModel());
        body.put("stream", stream);
        body.put("max_tokens", outputLimit);
        ArrayNode messages = body.putArray("messages");
        List<String> system = new ArrayList<>();
        for (JsonNode message : request.path("messages")) {
            String role = message.path("role").asText();
            if ("system".equals(role)) {
                String text = textContent(message.path("content"));
                if (!text.isBlank()) system.add(text);
                continue;
            }
            if ("tool".equals(role)) {
                ArrayNode content = objectMapper.createArrayNode();
                ObjectNode result = content.addObject();
                result.put("type", "tool_result");
                result.put("tool_use_id", message.path("toolCallId").asText());
                result.put("content", textContent(message.path("content")));
                appendAnthropicMessage(messages, "user", content);
                continue;
            }
            JsonNode restored = restoredAnthropicContent(message.path("providerState"), model);
            ArrayNode content = restored instanceof ArrayNode array
                    ? array.deepCopy() : anthropicContent(message);
            appendAnthropicMessage(messages, "assistant".equals(role) ? "assistant" : "user", content);
        }
        if (!system.isEmpty()) {
            body.put("system", String.join("\n\n", system));
        }
        ArrayNode tools = anthropicTools(request.path("tools"));
        if (webSearchRequested(request)) {
            ObjectNode webSearch = tools.addObject();
            webSearch.put("type", "web_search_20250305");
            webSearch.put("name", "web_search");
        }
        if (!tools.isEmpty()) {
            body.set("tools", tools);
            body.putObject("tool_choice").put("type", "auto");
        }
        String effort = reasoningEffort(request);
        if (!effort.isBlank() && !"none".equals(effort) && outputLimit > 1024) {
            long budget = switch (effort) {
                case "low" -> 1024;
                case "high", "xhigh", "max", "ultra" -> Math.max(1024, outputLimit * 3 / 4);
                default -> Math.max(1024, outputLimit / 2);
            };
            body.putObject("thinking")
                    .put("type", "enabled")
                    .put("budget_tokens", Math.min(outputLimit - 1, budget));
        }
        return body;
    }

    private ArrayNode openAiMessages(JsonNode source) {
        ArrayNode messages = objectMapper.createArrayNode();
        for (JsonNode message : source) {
            ObjectNode converted = messages.addObject();
            String role = message.path("role").asText();
            converted.put("role", role);
            JsonNode content = message.path("content");
            if (hasImage(content)) {
                ArrayNode blocks = converted.putArray("content");
                for (JsonNode part : content) {
                    if ("image".equals(part.path("type").asText())) {
                        ObjectNode image = blocks.addObject();
                        image.put("type", "image_url");
                        image.putObject("image_url").put("url", part.path("url").asText());
                    } else if ("text".equals(part.path("type").asText())) {
                        blocks.addObject().put("type", "text").put("text", part.path("text").asText());
                    }
                }
            } else {
                converted.put("content", textContent(content));
            }
            if ("assistant".equals(role) && message.path("toolCalls").isArray()
                    && !message.path("toolCalls").isEmpty()) {
                ArrayNode calls = converted.putArray("tool_calls");
                for (JsonNode call : message.path("toolCalls")) {
                    ObjectNode target = calls.addObject();
                    target.put("id", call.path("id").asText());
                    target.put("type", "function");
                    target.putObject("function")
                            .put("name", call.path("name").asText())
                            .put("arguments", call.path("arguments").asText("{}"));
                }
            }
            if ("tool".equals(role)) {
                converted.put("tool_call_id", message.path("toolCallId").asText());
            }
        }
        return messages;
    }

    private ArrayNode anthropicContent(JsonNode message) {
        ArrayNode content = objectMapper.createArrayNode();
        for (JsonNode part : message.path("content")) {
            String type = part.path("type").asText();
            if ("text".equals(type)) {
                content.addObject().put("type", "text").put("text", part.path("text").asText());
            } else if ("image".equals(type)) {
                DataUrl image = dataUrl(part.path("url").asText());
                ObjectNode source = content.addObject().put("type", "image").putObject("source");
                source.put("type", "base64");
                source.put("media_type", image.mediaType());
                source.put("data", image.data());
            }
        }
        for (JsonNode call : message.path("toolCalls")) {
            ObjectNode tool = content.addObject();
            tool.put("type", "tool_use");
            tool.put("id", call.path("id").asText());
            tool.put("name", call.path("name").asText());
            tool.set("input", jsonObject(call.path("arguments").asText("{}")));
        }
        return content;
    }

    private void appendAnthropicMessage(ArrayNode messages, String role, ArrayNode content) {
        if (content.isEmpty()) return;
        JsonNode last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (last instanceof ObjectNode previous && role.equals(previous.path("role").asText())
                && previous.path("content") instanceof ArrayNode previousContent) {
            previousContent.addAll(content);
            return;
        }
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.set("content", content);
    }

    private JsonNode restoredAnthropicContent(JsonNode state, ResolvedModelConfig model) {
        if (!state.isObject()
                || !"ANTHROPIC".equals(state.path("protocol").asText())
                || !model.modelCode().equals(state.path("modelCode").asText())
                || !model.providerCode().equals(state.path("providerCode").asText())
                || !state.path("content").isArray()) {
            return null;
        }
        return state.path("content");
    }

    private ArrayNode openAiTools(JsonNode source) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (JsonNode tool : source) {
            ObjectNode target = tools.addObject();
            target.put("type", "function");
            ObjectNode function = target.putObject("function");
            function.put("name", tool.path("name").asText());
            function.put("description", tool.path("description").asText());
            function.set("parameters", schema(tool));
        }
        return tools;
    }

    private ArrayNode responsesTools(JsonNode source) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (JsonNode tool : source) {
            ObjectNode target = tools.addObject();
            target.put("type", "function");
            target.put("name", tool.path("name").asText());
            target.put("description", tool.path("description").asText());
            target.set("parameters", schema(tool));
        }
        return tools;
    }

    private ArrayNode anthropicTools(JsonNode source) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (JsonNode tool : source) {
            ObjectNode target = tools.addObject();
            target.put("name", tool.path("name").asText());
            target.put("description", tool.path("description").asText());
            target.set("input_schema", schema(tool));
        }
        return tools;
    }

    private JsonNode schema(JsonNode tool) {
        JsonNode schema = tool.get("inputSchema");
        return schema != null && schema.isObject()
                ? schema.deepCopy() : objectMapper.createObjectNode().put("type", "object");
    }

    private ObjectNode result(JsonNode payload, ResolvedModelConfig model, ProviderProtocol protocol) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("content", "");
        result.put("reasoning", "");
        result.put("model", model.modelCode());
        ArrayNode calls = result.putArray("toolCalls");
        switch (protocol) {
            case OPENAI_COMPATIBLE -> openAiResult(payload, result, calls);
            case ANTHROPIC -> anthropicResult(payload, result, calls, model);
            case RESPONSES -> responsesResult(payload, result, calls);
        }
        return result;
    }

    private void openAiResult(JsonNode payload, ObjectNode result, ArrayNode calls) {
        JsonNode message = payload.path("choices").path(0).path("message");
        result.put("content", outputText(message.get("content")));
        result.put("reasoning", firstText(message, "reasoning_content", "reasoning"));
        appendOpenAiCalls(calls, message.path("tool_calls"));
    }

    private void anthropicResult(
            JsonNode payload,
            ObjectNode result,
            ArrayNode calls,
            ResolvedModelConfig model
    ) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (JsonNode block : payload.path("content")) {
            switch (block.path("type").asText()) {
                case "text" -> content.append(block.path("text").asText());
                case "thinking" -> reasoning.append(block.path("thinking").asText());
                case "tool_use" -> addToolCall(calls, block.path("id").asText(),
                        block.path("name").asText(), json(block.path("input")));
                default -> { }
            }
        }
        result.put("content", content.toString());
        result.put("reasoning", reasoning.toString());
        result.put("model", model.modelCode());
        if (payload.path("content").isArray()) {
            result.set("providerState", anthropicState(model, payload.path("content")));
        }
    }

    private void responsesResult(JsonNode payload, ObjectNode result, ArrayNode calls) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (JsonNode item : payload.path("output")) {
            switch (item.path("type").asText()) {
                case "message" -> {
                    for (JsonNode block : item.path("content")) {
                        if ("output_text".equals(block.path("type").asText())) {
                            content.append(block.path("text").asText());
                        } else if ("refusal".equals(block.path("type").asText())) {
                            content.append(block.path("refusal").asText());
                        }
                    }
                }
                case "function_call" -> addToolCall(calls,
                        item.path("call_id").asText(item.path("id").asText()),
                        item.path("name").asText(), item.path("arguments").asText("{}"));
                case "reasoning" -> {
                    for (JsonNode summary : item.path("summary")) {
                        if ("summary_text".equals(summary.path("type").asText())) {
                            reasoning.append(summary.path("text").asText());
                        }
                    }
                }
                default -> { }
            }
        }
        result.put("content", content.toString());
        result.put("reasoning", reasoning.toString());
    }

    private void appendOpenAiCalls(ArrayNode calls, JsonNode source) {
        for (JsonNode call : source) {
            JsonNode function = call.path("function");
            addToolCall(calls, call.path("id").asText(), function.path("name").asText(),
                    function.path("arguments").asText("{}"));
        }
    }

    private void addToolCall(ArrayNode calls, String id, String name, String arguments) {
        calls.addObject().put("id", id.isBlank() ? UUID.randomUUID().toString() : id)
                .put("name", name).put("arguments", arguments.isBlank() ? "{}" : arguments);
    }

    private ObjectNode envelope(String requestId, String modelCode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("protocolVersion", VERSION);
        response.put("requestId", requestId);
        response.put("model", modelCode);
        return response;
    }

    private ObjectNode usage(TokenUsage usage) {
        long prompt = usage.inputTokens() + usage.cacheReadTokens() + usage.cacheWriteTokens();
        long completion = usage.outputTokens() + usage.reasoningTokens();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("promptTokens", prompt);
        node.put("completionTokens", completion);
        node.put("totalTokens", prompt + completion);
        node.put("inputTokens", usage.inputTokens());
        node.put("outputTokens", usage.outputTokens());
        node.put("reasoningTokens", usage.reasoningTokens());
        node.put("cacheReadTokens", usage.cacheReadTokens());
        node.put("cacheWriteTokens", usage.cacheWriteTokens());
        node.put("cacheMetricsAvailable", usage.cacheReadTokens() > 0 || usage.cacheWriteTokens() > 0);
        return node;
    }

    private ObjectNode anthropicState(ResolvedModelConfig model, JsonNode content) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("protocol", "ANTHROPIC");
        state.put("modelCode", model.modelCode());
        state.put("providerCode", model.providerCode());
        state.set("content", content.deepCopy());
        return state;
    }

    private void validateCapabilities(
            ObjectNode request,
            ResolvedModelConfig model,
            ProviderProtocol protocol
    ) {
        if (!request.path("tools").isEmpty() && !model.capabilities().tools()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_TOOLS_UNSUPPORTED", "当前模型不支持工具调用");
        }
        String effort = reasoningEffort(request);
        if (!effort.isBlank() && !"none".equals(effort) && !model.capabilities().reasoning()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_REASONING_UNSUPPORTED", "当前模型不支持推理参数");
        }
        if (containsImage(request.path("messages")) && !model.capabilities().vision()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_VISION_UNSUPPORTED", "当前模型不支持图片输入");
        }
        if (webSearchRequested(request) && !model.capabilities().webSearch()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_WEB_SEARCH_UNSUPPORTED",
                    "当前模型未启用供应商托管 Web Search");
        }
        if (webSearchRequested(request) && protocol == ProviderProtocol.OPENAI_COMPATIBLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLOUD_WEB_SEARCH_PROTOCOL_UNSUPPORTED",
                    "Chat Completions 兼容协议没有统一的供应商托管 Web Search 规范");
        }
    }

    private boolean webSearchRequested(ObjectNode request) {
        return request.path("features").path("webSearch").asBoolean(false);
    }

    private boolean containsImage(JsonNode node) {
        if (node == null) return false;
        if (node.isObject() && "image".equals(node.path("type").asText())) return true;
        if (node.isContainerNode()) {
            for (JsonNode child : node) if (containsImage(child)) return true;
        }
        return false;
    }

    private boolean hasImage(JsonNode content) {
        if (!content.isArray()) return false;
        for (JsonNode part : content) {
            if ("image".equals(part.path("type").asText())) return true;
        }
        return false;
    }

    private String textContent(JsonNode content) {
        if (content == null || content.isNull()) return "";
        if (content.isTextual()) return content.asText();
        StringBuilder value = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText())) value.append(part.path("text").asText());
        }
        return value.toString();
    }

    private String outputText(JsonNode content) {
        if (content == null || content.isNull()) return "";
        if (content.isTextual()) return content.asText();
        StringBuilder value = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText())) value.append(part.path("text").asText());
        }
        return value.toString();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) return value.asText();
        }
        return "";
    }

    private String reasoningEffort(ObjectNode request) {
        return request.path("generation").path("reasoningEffort").asText("").trim().toLowerCase();
    }

    private ObjectNode jsonObject(String value) {
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed instanceof ObjectNode object ? object : objectMapper.createObjectNode();
        } catch (JsonProcessingException ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value == null || value.isMissingNode()
                    ? objectMapper.createObjectNode() : value);
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private DataUrl dataUrl(String value) {
        int separator = value.indexOf(',');
        if (!value.startsWith("data:") || separator < 6 || !value.substring(5, separator).endsWith(";base64")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_CONTENT", "图片必须使用 base64 data URL");
        }
        String metadata = value.substring(5, separator);
        return new DataUrl(metadata.substring(0, metadata.length() - ";base64".length()), value.substring(separator + 1));
    }

    private record DataUrl(String mediaType, String data) { }

    private final class StreamTranslator {
        private final ResolvedModelConfig model;
        private final ProviderProtocol protocol;
        private final String requestId;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolParts> tools = new LinkedHashMap<>();
        private final Map<Integer, ObjectNode> anthropicBlocks = new LinkedHashMap<>();
        private final Map<Integer, String> anthropicSearchIndices = new LinkedHashMap<>();
        private final Map<String, WebSearchParts> searches = new LinkedHashMap<>();
        private final ObjectNode anthropicUsage = objectMapper.createObjectNode();
        private TokenUsage finalUsage;
        private ApiException terminalError;
        private String resolvedModel;
        private boolean discardLine;
        private boolean completed;

        private StreamTranslator(ResolvedModelConfig model, ProviderProtocol protocol, String requestId) {
            this.model = model;
            this.protocol = protocol;
            this.requestId = requestId;
            this.resolvedModel = model.modelCode();
        }

        private List<DataBuffer> accept(byte[] bytes) {
            List<DataBuffer> output = new ArrayList<>();
            for (byte value : bytes) {
                if (value == '\n') {
                    if (!discardLine) translateLine(line.toByteArray(), output);
                    line.reset();
                    discardLine = false;
                } else if (!discardLine) {
                    if (line.size() >= MAX_EVENT_LINE_BYTES) {
                        line.reset();
                        discardLine = true;
                    } else {
                        line.write(value);
                    }
                }
            }
            return output;
        }

        private List<DataBuffer> finish() {
            List<DataBuffer> output = new ArrayList<>();
            if (!discardLine && line.size() > 0) translateLine(line.toByteArray(), output);
            line.reset();
            if (terminalError != null) throw terminalError;
            if (!completed) emitCompleted(output);
            return output;
        }

        private void translateLine(byte[] bytes, List<DataBuffer> output) {
            if (terminalError != null) return;
            String value = new String(bytes, StandardCharsets.UTF_8).strip();
            if (!value.startsWith("data:")) return;
            String data = value.substring(5).trim();
            if (data.isEmpty()) return;
            if ("[DONE]".equals(data)) {
                emitCompleted(output);
                return;
            }
            try {
                JsonNode event = objectMapper.readTree(data);
                switch (protocol) {
                    case OPENAI_COMPATIBLE -> openAiEvent(event, output);
                    case ANTHROPIC -> anthropicEvent(event, output);
                    case RESPONSES -> responsesEvent(event, output);
                }
            } catch (ApiException error) {
                throw error;
            } catch (Exception error) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_STREAM_INVALID",
                        "模型供应商返回了无法解析的流式事件", error);
            }
        }

        private void openAiEvent(JsonNode event, List<DataBuffer> output) {
            JsonNode delta = event.path("choices").path(0).path("delta");
            emitTextDelta("reasoning_delta", firstText(delta, "reasoning_content", "reasoning"), reasoning, output);
            emitTextDelta("content_delta", outputText(delta.get("content")), content, output);
            for (JsonNode call : delta.path("tool_calls")) {
                int index = call.path("index").asInt(0);
                ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String arguments = call.path("function").path("arguments").asText();
                parts.merge(id, name, arguments);
                emitToolDelta(index, id, name, arguments, output);
            }
            TokenUsage usage = usageParser.parse(ProviderProtocol.OPENAI_COMPATIBLE, event);
            if (usage != null) emitUsage(usage, output);
        }

        private void anthropicEvent(JsonNode event, List<DataBuffer> output) {
            String type = event.path("type").asText();
            if ("message_start".equals(type)) {
                JsonNode message = event.path("message");
                merge(anthropicUsage, message.path("usage"));
                return;
            }
            int index = event.path("index").asInt(0);
            if ("content_block_start".equals(type)) {
                ObjectNode block = event.path("content_block") instanceof ObjectNode object
                        ? object.deepCopy() : objectMapper.createObjectNode();
                anthropicBlocks.put(index, block);
                String blockType = block.path("type").asText();
                if ("text".equals(blockType)) {
                    emitTextDelta("content_delta", block.path("text").asText(), content, output);
                } else if ("thinking".equals(blockType)) {
                    emitTextDelta("reasoning_delta", block.path("thinking").asText(), reasoning, output);
                } else if ("tool_use".equals(blockType)) {
                    ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                    String arguments = block.path("input").isObject() && !block.path("input").isEmpty()
                            ? json(block.path("input")) : "";
                    parts.merge(block.path("id").asText(), block.path("name").asText(), arguments);
                    emitToolDelta(index, block.path("id").asText(), block.path("name").asText(), arguments, output);
                } else if ("server_tool_use".equals(blockType)
                        && "web_search".equals(block.path("name").asText())) {
                    String searchId = block.path("id").asText("web-search-" + UUID.randomUUID());
                    WebSearchParts search = searches.computeIfAbsent(searchId, WebSearchParts::new);
                    search.updateQuery(searchQuery(block.path("input")));
                    anthropicSearchIndices.put(index, searchId);
                    emitSearchStarted(search, output);
                } else if ("web_search_tool_result".equals(blockType)) {
                    String searchId = block.path("tool_use_id").asText();
                    WebSearchParts search = searches.computeIfAbsent(
                            searchId.isBlank() ? "web-search-" + UUID.randomUUID() : searchId,
                            WebSearchParts::new
                    );
                    emitSearchCompleted(search, webSources(block.path("content")), output);
                }
                return;
            }
            if ("content_block_delta".equals(type)) {
                JsonNode delta = event.path("delta");
                ObjectNode block = anthropicBlocks.computeIfAbsent(index, ignored -> objectMapper.createObjectNode());
                switch (delta.path("type").asText()) {
                    case "text_delta" -> {
                        String text = delta.path("text").asText();
                        append(block, "text", text);
                        emitTextDelta("content_delta", text, content, output);
                    }
                    case "thinking_delta" -> {
                        String text = delta.path("thinking").asText();
                        append(block, "thinking", text);
                        emitTextDelta("reasoning_delta", text, reasoning, output);
                    }
                    case "signature_delta" -> append(block, "signature", delta.path("signature").asText());
                    case "input_json_delta" -> {
                        String fragment = delta.path("partial_json").asText();
                        String current = block.path("_partialInput").asText();
                        block.put("_partialInput", current + fragment);
                        if (!("server_tool_use".equals(block.path("type").asText())
                                && "web_search".equals(block.path("name").asText()))) {
                            ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                            parts.merge("", "", fragment);
                            emitToolDelta(index, "", "", fragment, output);
                        }
                    }
                    default -> { }
                }
                return;
            }
            if ("content_block_stop".equals(type)) {
                ObjectNode block = anthropicBlocks.get(index);
                if (block != null && block.has("_partialInput")) {
                    block.set("input", jsonObject(block.remove("_partialInput").asText()));
                }
                String searchId = anthropicSearchIndices.get(index);
                if (searchId != null && block != null) {
                    WebSearchParts search = searches.get(searchId);
                    search.updateQuery(searchQuery(block.path("input")));
                    emitSearchProgress(search, "正在检索网页…", output);
                }
                return;
            }
            if ("message_delta".equals(type)) {
                merge(anthropicUsage, event.path("usage"));
                if (event.path("usage").has("output_tokens")) {
                    TokenUsage usage = usageParser.parseUsage(ProviderProtocol.ANTHROPIC, anthropicUsage);
                    emitUsage(usage, output);
                }
                return;
            }
            if ("error".equals(type)) {
                emitSearchFailures("网络搜索失败", output);
                terminalError = new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_STREAM_ERROR",
                        "模型供应商返回流式错误");
            }
            if ("message_stop".equals(type)) emitCompleted(output);
        }

        private void responsesEvent(JsonNode event, List<DataBuffer> output) {
            String type = event.path("type").asText();
            if ("response.created".equals(type)) {
            } else if ("response.output_text.delta".equals(type) || "response.refusal.delta".equals(type)) {
                emitTextDelta("content_delta", event.path("delta").asText(), content, output);
            } else if ("response.reasoning_summary_text.delta".equals(type)) {
                emitTextDelta("reasoning_delta", event.path("delta").asText(), reasoning, output);
            } else if ("response.output_item.added".equals(type)
                    && "function_call".equals(event.path("item").path("type").asText())) {
                JsonNode item = event.path("item");
                int index = event.path("output_index").asInt(tools.size());
                ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                parts.merge(item.path("call_id").asText(item.path("id").asText()),
                        item.path("name").asText(), item.path("arguments").asText());
                emitToolDelta(index, parts.id, parts.name, item.path("arguments").asText(), output);
            } else if ("response.output_item.added".equals(type)
                    && "web_search_call".equals(event.path("item").path("type").asText())) {
                WebSearchParts search = responseSearch(event.path("item"), event);
                emitSearchStarted(search, output);
            } else if ("response.function_call_arguments.delta".equals(type)) {
                int index = event.path("output_index").asInt(0);
                ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                String delta = event.path("delta").asText();
                parts.merge("", "", delta);
                emitToolDelta(index, "", "", delta, output);
            } else if ("response.output_item.done".equals(type)
                    && "function_call".equals(event.path("item").path("type").asText())) {
                JsonNode item = event.path("item");
                int index = event.path("output_index").asInt(0);
                ToolParts parts = tools.computeIfAbsent(index, ignored -> new ToolParts());
                parts.replace(item.path("call_id").asText(item.path("id").asText()),
                        item.path("name").asText(), item.path("arguments").asText("{}"));
            } else if ("response.output_item.done".equals(type)
                    && "web_search_call".equals(event.path("item").path("type").asText())) {
                WebSearchParts search = responseSearch(event.path("item"), event);
                emitSearchProgress(search, "已完成检索，正在整理结果…", output);
            } else if ("response.web_search_call.in_progress".equals(type)) {
                emitSearchStarted(responseSearch(event.path("item"), event), output);
            } else if ("response.web_search_call.searching".equals(type)) {
                emitSearchProgress(responseSearch(event.path("item"), event), "正在检索网页…", output);
            } else if ("response.web_search_call.completed".equals(type)) {
                emitSearchProgress(responseSearch(event.path("item"), event), "已完成检索，正在整理结果…", output);
            } else if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                JsonNode response = event.path("response");
                TokenUsage usage = usageParser.parse(ProviderProtocol.RESPONSES, response);
                if (usage != null) emitUsage(usage, output);
                ObjectNode parsed = result(response, model, ProviderProtocol.RESPONSES);
                if (content.isEmpty()) content.append(parsed.path("content").asText());
                if (reasoning.isEmpty()) reasoning.append(parsed.path("reasoning").asText());
                if (tools.isEmpty()) readCanonicalTools(parsed.path("toolCalls"));
                completeResponseSearches(response, output);
                emitCompleted(output);
            } else if ("response.failed".equals(type) || "error".equals(type)) {
                emitSearchFailures("网络搜索失败", output);
                terminalError = new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_STREAM_ERROR",
                        "模型供应商返回流式错误");
            }
        }

        private WebSearchParts responseSearch(JsonNode item, JsonNode event) {
            String id = firstText(item, "id");
            if (id.isBlank()) id = firstText(event, "item_id", "id");
            if (id.isBlank()) id = "web-search-" + event.path("output_index").asText(UUID.randomUUID().toString());
            WebSearchParts search = searches.computeIfAbsent(id, WebSearchParts::new);
            JsonNode action = item.path("action").isObject() ? item.path("action") : event.path("action");
            search.updateQuery(searchQuery(action));
            return search;
        }

        private void completeResponseSearches(JsonNode response, List<DataBuffer> output) {
            ArrayNode citations = responseCitations(response);
            for (JsonNode item : response.path("output")) {
                if (!"web_search_call".equals(item.path("type").asText())) continue;
                WebSearchParts search = responseSearch(item, objectMapper.createObjectNode());
                ArrayNode sources = webSources(item.path("action").path("sources"));
                emitSearchCompleted(search, sources.isEmpty() ? citations : sources, output);
            }
            for (WebSearchParts search : searches.values()) {
                if (!search.completed) emitSearchCompleted(search, citations, output);
            }
        }

        private ArrayNode responseCitations(JsonNode response) {
            ArrayNode raw = objectMapper.createArrayNode();
            for (JsonNode item : response.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode block : item.path("content")) {
                    for (JsonNode annotation : block.path("annotations")) raw.add(annotation);
                }
            }
            return webSources(raw);
        }

        private void readCanonicalTools(JsonNode source) {
            int index = 0;
            for (JsonNode call : source) {
                ToolParts parts = new ToolParts();
                parts.replace(call.path("id").asText(), call.path("name").asText(),
                        call.path("arguments").asText("{}"));
                tools.put(index++, parts);
            }
        }

        private void emitTextDelta(String type, String delta, StringBuilder target, List<DataBuffer> output) {
            if (delta == null || delta.isEmpty()) return;
            target.append(delta);
            ObjectNode event = baseEvent(type);
            event.put("delta", delta);
            emit(event, output);
        }

        private void emitToolDelta(int index, String id, String name, String arguments, List<DataBuffer> output) {
            ObjectNode event = baseEvent("tool_call_delta");
            event.put("index", index);
            if (!id.isEmpty()) event.put("id", id);
            if (!name.isEmpty()) event.put("name", name);
            if (!arguments.isEmpty()) event.put("arguments", arguments);
            emit(event, output);
        }

        private void emitUsage(TokenUsage usage, List<DataBuffer> output) {
            if (usage == null) return;
            finalUsage = usage;
            ObjectNode event = baseEvent("usage");
            event.set("usage", usage(usage));
            emit(event, output);
        }

        private void emitSearchStarted(WebSearchParts search, List<DataBuffer> output) {
            if (search.started) return;
            search.started = true;
            ObjectNode event = baseEvent("web_search_started");
            event.put("itemId", search.id);
            event.put("query", search.query);
            emit(event, output);
        }

        private void emitSearchProgress(WebSearchParts search, String delta, List<DataBuffer> output) {
            emitSearchStarted(search, output);
            ObjectNode event = baseEvent("web_search_progress");
            event.put("itemId", search.id);
            event.put("query", search.query);
            event.put("delta", delta);
            emit(event, output);
        }

        private void emitSearchCompleted(WebSearchParts search, ArrayNode sources, List<DataBuffer> output) {
            if (search.completed) return;
            emitSearchStarted(search, output);
            search.completed = true;
            ObjectNode event = baseEvent("web_search_completed");
            event.put("itemId", search.id);
            event.put("query", search.query);
            event.set("sources", sources);
            emit(event, output);
        }

        private void emitSearchFailures(String message, List<DataBuffer> output) {
            for (WebSearchParts search : searches.values()) {
                if (search.completed) continue;
                ObjectNode event = baseEvent("web_search_failed");
                event.put("itemId", search.id);
                event.put("query", search.query);
                event.put("errorMessage", message);
                emit(event, output);
            }
        }

        private void emitCompleted(List<DataBuffer> output) {
            if (completed) return;
            completed = true;
            ObjectNode result = objectMapper.createObjectNode();
            result.put("content", content.toString());
            result.put("reasoning", reasoning.toString());
            result.put("model", resolvedModel);
            ArrayNode calls = result.putArray("toolCalls");
            tools.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> addToolCall(calls, entry.getValue().id,
                            entry.getValue().name, entry.getValue().arguments));
            if (protocol == ProviderProtocol.ANTHROPIC && !anthropicBlocks.isEmpty()) {
                ArrayNode blocks = objectMapper.createArrayNode();
                anthropicBlocks.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue).forEach(block -> {
                            JsonNode partial = block.remove("_partialInput");
                            if (partial != null) block.set("input", jsonObject(partial.asText()));
                            blocks.add(block);
                        });
                result.set("providerState", anthropicState(model, blocks));
            }
            ObjectNode event = baseEvent("completed");
            event.set("result", result);
            if (finalUsage != null) event.set("usage", usage(finalUsage));
            emit(event, output);
            emitRaw("data: [DONE]\n\n", output);
        }

        private ObjectNode baseEvent(String type) {
            ObjectNode event = envelope(requestId, model.modelCode());
            event.put("type", type);
            event.put("resolvedModel", resolvedModel);
            return event;
        }

        private void emit(ObjectNode event, List<DataBuffer> output) {
            try {
                emitRaw("data: " + objectMapper.writeValueAsString(event) + "\n\n", output);
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Could not serialize LUMORA stream event", error);
            }
        }

        private void emitRaw(String value, List<DataBuffer> output) {
            output.add(DefaultDataBufferFactory.sharedInstance.wrap(value.getBytes(StandardCharsets.UTF_8)));
        }

        private void append(ObjectNode object, String field, String value) {
            object.put(field, object.path(field).asText() + value);
        }

        private void merge(ObjectNode target, JsonNode source) {
            if (source != null && source.isObject()) {
                source.properties().forEach(entry -> target.set(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static final class ToolParts {
        private String id = "";
        private String name = "";
        private String arguments = "";

        private void merge(String id, String name, String arguments) {
            if (id != null && !id.isEmpty()) this.id = id;
            if (name != null) this.name += name;
            if (arguments != null) this.arguments += arguments;
        }

        private void replace(String id, String name, String arguments) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.arguments = arguments == null ? "" : arguments;
        }
    }

    private String searchQuery(JsonNode value) {
        String query = value.path("query").asText().trim();
        if (!query.isBlank()) return query;
        JsonNode queries = value.path("queries");
        if (queries.isArray() && !queries.isEmpty()) return queries.path(0).asText().trim();
        String url = value.path("url").asText().trim();
        String pattern = value.path("pattern").asText().trim();
        if (!url.isBlank() && !pattern.isBlank()) return url + " · " + pattern;
        return !url.isBlank() ? url : pattern;
    }

    private ArrayNode webSources(JsonNode rawSources) {
        ArrayNode sources = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        if (!rawSources.isArray() && !rawSources.isObject()) return sources;
        Iterable<JsonNode> candidates = rawSources.isArray() ? rawSources : List.of(rawSources);
        for (JsonNode raw : candidates) {
            JsonNode citation = raw.path("url_citation");
            String url = firstText(raw, "url");
            if (url.isBlank()) url = firstText(citation, "url");
            if (url.isBlank() || !seen.add(url)) continue;
            String title = firstText(raw, "title");
            if (title.isBlank()) title = firstText(citation, "title");
            ObjectNode source = sources.addObject();
            source.put("title", (title.isBlank() ? url : title).substring(0, Math.min(500, (title.isBlank() ? url : title).length())));
            source.put("url", url.substring(0, Math.min(2_000, url.length())));
            if (sources.size() >= 12) break;
        }
        return sources;
    }

    private static final class WebSearchParts {
        private final String id;
        private String query = "";
        private boolean started;
        private boolean completed;

        private WebSearchParts(String id) {
            this.id = id;
        }

        private void updateQuery(String value) {
            if (value != null && !value.isBlank()) query = value;
        }
    }
}
