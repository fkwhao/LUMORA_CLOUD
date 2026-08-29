package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderUsageParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProviderUsageParser parser = new ProviderUsageParser();

    @Test
    void splitsOpenAiCachedInputAndReasoningFromProviderTotals() throws Exception {
        var usage = parser.parse(ProviderProtocol.OPENAI_COMPATIBLE, objectMapper.readTree("""
                {
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 50,
                    "prompt_tokens_details": {
                      "cached_tokens": 20,
                      "cache_write_tokens": 5
                    },
                    "completion_tokens_details": {"reasoning_tokens": 10}
                  }
                }
                """));

        assertThat(usage.inputTokens()).isEqualTo(75);
        assertThat(usage.outputTokens()).isEqualTo(40);
        assertThat(usage.reasoningTokens()).isEqualTo(10);
        assertThat(usage.cacheReadTokens()).isEqualTo(20);
        assertThat(usage.cacheWriteTokens()).isEqualTo(5);
    }

    @Test
    void parsesResponsesUsageDetails() throws Exception {
        var usage = parser.parse(ProviderProtocol.RESPONSES, objectMapper.readTree("""
                {
                  "usage": {
                    "input_tokens": 140,
                    "output_tokens": 60,
                    "input_tokens_details": {"cached_tokens": 40},
                    "output_tokens_details": {"reasoning_tokens": 15}
                  }
                }
                """));

        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(45);
        assertThat(usage.reasoningTokens()).isEqualTo(15);
        assertThat(usage.cacheReadTokens()).isEqualTo(40);
    }

    @Test
    void parsesAnthropicUsageAndCacheBreakdown() throws Exception {
        var usage = parser.parse(ProviderProtocol.ANTHROPIC, objectMapper.readTree("""
                {
                  "usage": {
                    "input_tokens": 90,
                    "output_tokens": 30,
                    "cache_read_input_tokens": 50,
                    "cache_creation_input_tokens": 10
                  }
                }
                """));

        assertThat(usage.inputTokens()).isEqualTo(90);
        assertThat(usage.outputTokens()).isEqualTo(30);
        assertThat(usage.cacheReadTokens()).isEqualTo(50);
        assertThat(usage.cacheWriteTokens()).isEqualTo(10);
    }

    @Test
    void supportsOpenAiNativeCacheHitAndMissFields() throws Exception {
        var usage = parser.parse(ProviderProtocol.OPENAI_COMPATIBLE, objectMapper.readTree("""
                {
                  "usage": {
                    "prompt_tokens": 120,
                    "completion_tokens": 30,
                    "prompt_cache_hit_tokens": 80,
                    "prompt_cache_miss_tokens": 40
                  }
                }
                """));

        assertThat(usage.inputTokens()).isEqualTo(40);
        assertThat(usage.cacheReadTokens()).isEqualTo(80);
        assertThat(usage.outputTokens()).isEqualTo(30);
    }
}
