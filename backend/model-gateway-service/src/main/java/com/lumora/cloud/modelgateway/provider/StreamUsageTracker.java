package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.domain.TokenUsage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class StreamUsageTracker {

    private static final int MAX_EVENT_LINE_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ProviderUsageParser usageParser;
    private final ProviderProtocol protocol;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final ObjectNode anthropicUsage;
    private TokenUsage authoritativeUsage;
    private boolean discardCurrentLine;

    public StreamUsageTracker(
            ObjectMapper objectMapper,
            ProviderUsageParser usageParser,
            ProviderProtocol protocol
    ) {
        this.objectMapper = objectMapper;
        this.usageParser = usageParser;
        this.protocol = protocol;
        this.anthropicUsage = objectMapper.createObjectNode();
    }

    public void accept(byte[] bytes) {
        for (byte value : bytes) {
            if (value == '\n') {
                if (!discardCurrentLine) {
                    parseLine(line.toByteArray());
                }
                line.reset();
                discardCurrentLine = false;
            } else if (!discardCurrentLine) {
                if (line.size() >= MAX_EVENT_LINE_BYTES) {
                    line.reset();
                    discardCurrentLine = true;
                } else {
                    line.write(value);
                }
            }
        }
    }

    public void finish() {
        if (!discardCurrentLine && line.size() > 0) {
            parseLine(line.toByteArray());
        }
        line.reset();
    }

    public TokenUsage usage() {
        return authoritativeUsage;
    }

    private void parseLine(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.UTF_8).strip();
        if (!value.startsWith("data:")) {
            return;
        }
        String data = value.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(data);
            TokenUsage parsed = switch (protocol) {
                case OPENAI_COMPATIBLE -> usageParser.parse(protocol, event);
                case RESPONSES -> responsesUsage(event);
                case ANTHROPIC -> anthropicUsage(event);
            };
            if (parsed != null) {
                authoritativeUsage = parsed;
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // The response remains transparent to the client; malformed non-usage events are not billing facts.
        }
    }

    private TokenUsage responsesUsage(JsonNode event) {
        String type = event.path("type").asText();
        if (!"response.completed".equals(type)
                && !"response.incomplete".equals(type)
                && !"response.failed".equals(type)) {
            return null;
        }
        return usageParser.parse(ProviderProtocol.RESPONSES, event.get("response"));
    }

    private TokenUsage anthropicUsage(JsonNode event) {
        String type = event.path("type").asText();
        if ("message_start".equals(type)) {
            mergeUsage(event.path("message").get("usage"));
            return null;
        }
        if (!"message_delta".equals(type)) {
            return null;
        }
        JsonNode usage = event.get("usage");
        mergeUsage(usage);
        if (usage == null || !usage.isObject() || !usage.has("output_tokens")) {
            return null;
        }
        return usageParser.parseUsage(ProviderProtocol.ANTHROPIC, anthropicUsage);
    }

    private void mergeUsage(JsonNode update) {
        if (update == null || !update.isObject()) {
            return;
        }
        update.properties().forEach(entry -> anthropicUsage.set(entry.getKey(), entry.getValue()));
    }
}
