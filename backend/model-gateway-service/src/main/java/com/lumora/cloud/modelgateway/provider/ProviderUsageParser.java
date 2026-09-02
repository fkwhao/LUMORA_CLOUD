package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import com.lumora.cloud.modelgateway.domain.TokenUsage;
import org.springframework.stereotype.Component;

@Component
public class ProviderUsageParser {

    public TokenUsage parse(ProviderProtocol protocol, JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode usage = root.get("usage");
        return parseUsage(protocol, usage);
    }

    public TokenUsage parseUsage(ProviderProtocol protocol, JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return null;
        }
        return switch (protocol) {
            case OPENAI_COMPATIBLE -> parseOpenAi(usage);
            case RESPONSES -> parseResponses(usage);
            case ANTHROPIC -> parseAnthropic(usage);
        };
    }

    private TokenUsage parseOpenAi(JsonNode usage) {
        long prompt = nonNegative(usage, "prompt_tokens");
        long completion = nonNegative(usage, "completion_tokens");
        JsonNode promptDetails = object(first(usage, "prompt_tokens_details", "input_tokens_details"));
        JsonNode completionDetails = object(first(usage, "completion_tokens_details", "output_tokens_details"));

        Long nativeCacheRead = present(usage, "prompt_cache_hit_tokens");
        Long nativeCacheMiss = present(usage, "prompt_cache_miss_tokens");
        long cacheRead = coalesce(
                present(promptDetails, "cached_tokens", "cache_read_tokens", "cache_read_input_tokens"),
                nativeCacheRead,
                present(usage, "cache_read_tokens", "cache_read_input_tokens", "input_cache_read")
        );
        long cacheWrite = coalesce(
                present(promptDetails, "cache_write_tokens", "cache_creation_input_tokens"),
                present(usage, "cache_write_tokens", "cache_creation_input_tokens", "input_cache_write")
        );
        long reasoning = Math.min(completion, coalesce(
                present(completionDetails, "reasoning_tokens"),
                present(usage, "reasoning_tokens")
        ));
        boolean promptIncludesCache = nativeCacheRead != null || nativeCacheMiss != null
                || has(promptDetails, "cached_tokens", "cache_read_tokens", "cache_write_tokens");
        long input = nativeCacheMiss != null
                ? nativeCacheMiss
                : (promptIncludesCache ? Math.max(0, prompt - cacheRead - cacheWrite) : prompt);
        return split(input, completion, reasoning, cacheRead, cacheWrite);
    }

    private TokenUsage parseResponses(JsonNode usage) {
        long inputTotal = nonNegative(usage, "input_tokens");
        long outputTotal = nonNegative(usage, "output_tokens");
        JsonNode inputDetails = object(usage.get("input_tokens_details"));
        JsonNode outputDetails = object(usage.get("output_tokens_details"));
        long cacheRead = coalesce(present(inputDetails, "cached_tokens", "cache_read_tokens"));
        long cacheWrite = coalesce(present(
                inputDetails, "cache_write_tokens", "cache_creation_input_tokens"
        ));
        long reasoning = Math.min(outputTotal, coalesce(present(outputDetails, "reasoning_tokens")));
        long input = Math.max(0, inputTotal - cacheRead - cacheWrite);
        return split(input, outputTotal, reasoning, cacheRead, cacheWrite);
    }

    private TokenUsage parseAnthropic(JsonNode usage) {
        long input = nonNegative(usage, "input_tokens");
        long outputTotal = nonNegative(usage, "output_tokens");
        long cacheRead = coalesce(present(usage, "cache_read_input_tokens", "cache_read_tokens"));
        long cacheWrite = coalesce(present(
                usage, "cache_creation_input_tokens", "cache_write_tokens"
        ));
        long reasoning = Math.min(outputTotal, coalesce(present(usage, "reasoning_tokens")));
        return split(input, outputTotal, reasoning, cacheRead, cacheWrite);
    }

    private TokenUsage split(
            long input,
            long outputTotal,
            long reasoning,
            long cacheRead,
            long cacheWrite
    ) {
        return new TokenUsage(
                Math.max(0, input),
                Math.max(0, outputTotal - reasoning),
                Math.max(0, reasoning),
                Math.max(0, cacheRead),
                Math.max(0, cacheWrite)
        );
    }

    private JsonNode first(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode object(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    private Long present(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return Math.max(0, value.asLong());
            }
        }
        return null;
    }

    private long nonNegative(JsonNode node, String name) {
        Long value = present(node, name);
        return value == null ? 0 : value;
    }

    private long coalesce(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    private boolean has(JsonNode node, String... names) {
        if (node == null) {
            return false;
        }
        for (String name : names) {
            if (node.has(name)) {
                return true;
            }
        }
        return false;
    }
}
