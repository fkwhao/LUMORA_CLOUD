package com.lumora.cloud.modelgateway.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.catalog.CatalogContracts.ResolvedModelConfig;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
import com.lumora.cloud.modelgateway.domain.model.ValidatedChatRequest;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ChatRequestValidator {

    public ValidatedChatRequest parse(JsonNode body, GatewayProtocol protocol) {
        if (!(body instanceof ObjectNode object)) {
            throw badRequest("请求体必须是 JSON 对象");
        }
        JsonNode modelNode = object.get("model");
        if (modelNode == null || !modelNode.isTextual() || modelNode.textValue().isBlank()
                || modelNode.textValue().length() > 128) {
            throw badRequest("model 不能为空且长度不能超过 128");
        }
        JsonNode streamNode = object.get("stream");
        if (streamNode != null && !streamNode.isBoolean()) {
            throw badRequest("stream 必须是布尔值");
        }
        if (protocol.isInternal()) {
            JsonNode version = object.get("protocolVersion");
            if (version == null || !version.isTextual() || !"1".equals(version.textValue())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LUMORA_PROTOCOL_VERSION_UNSUPPORTED",
                        "仅支持 LUMORA 内部协议版本 1");
            }
            requireArray(object, "messages");
        }
        long requestedOutput = requestedOutputLimit(object, protocol);
        return new ValidatedChatRequest(
                modelNode.textValue().trim().toLowerCase(Locale.ROOT),
                protocol,
                streamNode != null && streamNode.booleanValue(),
                requestedOutput,
                object.deepCopy()
        );
    }

    public ObjectNode upstreamBody(ValidatedChatRequest request, ResolvedModelConfig model) {
        ProviderProtocol modelProtocol;
        try {
            modelProtocol = ProviderProtocol.parse(model.protocolType());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_PROTOCOL_UNSUPPORTED",
                    "当前模型配置了云端不支持的 API 格式");
        }
        if (request.protocol().isInternal()) {
            throw new IllegalArgumentException("LUMORA internal requests must be translated by LumoraProtocolAdapter");
        }
        ProviderProtocol requestProtocol = request.protocol().providerProtocol();
        if (modelProtocol != requestProtocol) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_PROTOCOL_MISMATCH",
                    "当前模型不能通过该 API 格式调用");
        }
        ObjectNode body = request.originalBody().deepCopy();
        validateCapabilities(body, model);
        long outputLimit = Math.min(model.capabilities().maxOutputTokens(), request.requestedMaxOutputTokens());
        return switch (requestProtocol) {
            case OPENAI_COMPATIBLE -> openAiBody(body, model, request.stream(), outputLimit);
            case ANTHROPIC -> anthropicBody(body, model, request.stream(), outputLimit);
            case RESPONSES -> responsesBody(body, model, request.stream(), outputLimit);
        };
    }

    private ObjectNode openAiBody(
            ObjectNode body,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        requireArray(body, "messages");
        requireSingleGeneration(body, "n");
        requireSingleGeneration(body, "best_of");
        body.remove("user");
        if (body.has("store")) {
            body.put("store", false);
        }
        body.put("model", model.upstreamModel());
        cap(body, "max_tokens", outputLimit);
        cap(body, "max_completion_tokens", outputLimit);
        if (!body.has("max_tokens") && !body.has("max_completion_tokens")) {
            body.put("max_tokens", outputLimit);
        }
        if (stream) {
            ObjectNode streamOptions;
            if (body.get("stream_options") instanceof ObjectNode existing) {
                streamOptions = existing;
            } else {
                body.remove("stream_options");
                streamOptions = body.putObject("stream_options");
            }
            streamOptions.put("include_usage", true);
        }
        return body;
    }

    private ObjectNode anthropicBody(
            ObjectNode body,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        requireArray(body, "messages");
        body.remove("metadata");
        body.put("model", model.upstreamModel());
        body.put("stream", stream);
        cap(body, "max_tokens", outputLimit);
        if (!body.has("max_tokens")) {
            body.put("max_tokens", outputLimit);
        }
        return body;
    }

    private ObjectNode responsesBody(
            ObjectNode body,
            ResolvedModelConfig model,
            boolean stream,
            long outputLimit
    ) {
        JsonNode input = body.get("input");
        if (input == null || input.isNull() || (!input.isTextual() && !input.isArray())) {
            throw badRequest("input 必须是字符串或数组");
        }
        body.remove(java.util.List.of(
                "user", "safety_identifier", "prompt_cache_key", "max_tokens",
                "max_completion_tokens", "stream_options", "n", "best_of"
        ));
        body.put("store", false);
        body.put("model", model.upstreamModel());
        body.put("stream", stream);
        cap(body, "max_output_tokens", outputLimit);
        if (!body.has("max_output_tokens")) {
            body.put("max_output_tokens", outputLimit);
        }
        return body;
    }

    private void validateCapabilities(ObjectNode body, ResolvedModelConfig model) {
        if (body.has("tools") && body.get("tools").isArray() && !body.get("tools").isEmpty()
                && !model.capabilities().tools()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_TOOLS_UNSUPPORTED", "当前模型不支持工具调用");
        }
        if ((body.has("reasoning") || body.has("reasoning_effort") || body.has("thinking"))
                && !model.capabilities().reasoning()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_REASONING_UNSUPPORTED", "当前模型不支持推理参数");
        }
        if ((body.has("response_format") || hasNested(body, "text", "format")
                || hasNested(body, "output_config", "format")) && !model.capabilities().json()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_JSON_UNSUPPORTED", "当前模型不支持结构化 JSON 输出");
        }
        if ((containsImage(body.get("messages")) || containsImage(body.get("input")))
                && !model.capabilities().vision()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_VISION_UNSUPPORTED", "当前模型不支持图片输入");
        }
    }

    private boolean containsImage(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && type.isTextual()
                    && ("image_url".equals(type.textValue()) || "input_image".equals(type.textValue())
                    || "image".equals(type.textValue()))) {
                return true;
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsImage(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private long positiveLimit(ObjectNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null) {
            return Long.MAX_VALUE;
        }
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw badRequest(field + " 必须是正整数");
        }
        return value.longValue();
    }

    private long requestedOutputLimit(ObjectNode body, GatewayProtocol protocol) {
        return switch (protocol) {
            case ANTHROPIC -> positiveLimit(body, "max_tokens");
            case RESPONSES -> positiveLimit(body, "max_output_tokens");
            case OPENAI_COMPATIBLE -> minimumSpecified(
                    positiveLimit(body, "max_completion_tokens"),
                    positiveLimit(body, "max_tokens")
            );
            case LUMORA_INTERNAL -> internalOutputLimit(body);
        };
    }

    private long internalOutputLimit(ObjectNode body) {
        JsonNode generation = body.get("generation");
        if (generation == null || generation.isNull()) {
            return Long.MAX_VALUE;
        }
        if (!generation.isObject()) {
            throw badRequest("generation 必须是对象");
        }
        JsonNode value = generation.get("maxOutputTokens");
        if (value == null || value.isNull()) {
            return Long.MAX_VALUE;
        }
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw badRequest("generation.maxOutputTokens 必须是正整数");
        }
        return value.longValue();
    }

    private long minimumSpecified(long first, long second) {
        if (first == Long.MAX_VALUE) {
            return second;
        }
        return second == Long.MAX_VALUE ? first : Math.min(first, second);
    }

    private void cap(ObjectNode body, String field, long maximum) {
        JsonNode value = body.get(field);
        if (value != null && value.canConvertToLong()) {
            body.put(field, Math.min(value.longValue(), maximum));
        }
    }

    private void requireSingleGeneration(ObjectNode body, String field) {
        JsonNode value = body.get(field);
        if (value != null && (!value.canConvertToInt() || value.intValue() != 1)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MULTIPLE_GENERATIONS_UNSUPPORTED",
                    "云端套餐调用暂不支持 " + field + " 大于 1");
        }
    }

    private void requireArray(ObjectNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isArray()) {
            throw badRequest(field + " 必须是数组");
        }
    }

    private boolean hasNested(ObjectNode body, String parent, String child) {
        JsonNode value = body.get(parent);
        return value != null && value.isObject() && value.has(child);
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_REQUEST", message);
    }
}
