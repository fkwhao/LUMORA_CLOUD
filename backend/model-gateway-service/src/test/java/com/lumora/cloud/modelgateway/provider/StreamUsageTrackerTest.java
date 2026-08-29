package com.lumora.cloud.modelgateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.cloud.api.catalog.ProviderProtocol;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StreamUsageTrackerTest {

    @Test
    void extractsTerminalUsageAcrossArbitraryNetworkChunks() {
        StreamUsageTracker tracker = tracker(ProviderProtocol.OPENAI_COMPATIBLE);
        String event = "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}\n\n"
                + "data: [DONE]\n\n";
        byte[] bytes = event.getBytes(StandardCharsets.UTF_8);

        tracker.accept(java.util.Arrays.copyOfRange(bytes, 0, 17));
        tracker.accept(java.util.Arrays.copyOfRange(bytes, 17, 61));
        tracker.accept(java.util.Arrays.copyOfRange(bytes, 61, bytes.length));
        tracker.finish();

        assertThat(tracker.usage().inputTokens()).isEqualTo(12);
        assertThat(tracker.usage().outputTokens()).isEqualTo(5);
    }

    @Test
    void combinesAnthropicStartAndTerminalUsage() {
        StreamUsageTracker tracker = tracker(ProviderProtocol.ANTHROPIC);
        String events = "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":90,"
                + "\"cache_read_input_tokens\":40,\"output_tokens\":1}}}\n\n"
                + "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":18}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        byte[] bytes = events.getBytes(StandardCharsets.UTF_8);

        tracker.accept(java.util.Arrays.copyOfRange(bytes, 0, 53));
        tracker.accept(java.util.Arrays.copyOfRange(bytes, 53, bytes.length));
        tracker.finish();

        assertThat(tracker.usage().inputTokens()).isEqualTo(90);
        assertThat(tracker.usage().outputTokens()).isEqualTo(18);
        assertThat(tracker.usage().cacheReadTokens()).isEqualTo(40);
    }

    @Test
    void extractsResponsesUsageOnlyFromTerminalResponseEvent() {
        StreamUsageTracker tracker = tracker(ProviderProtocol.RESPONSES);
        String events = "data: {\"type\":\"response.created\",\"response\":{\"usage\":null}}\n\n"
                + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"ok\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{"
                + "\"input_tokens\":75,\"output_tokens\":12}}}\n\n";

        tracker.accept(events.getBytes(StandardCharsets.UTF_8));
        tracker.finish();

        assertThat(tracker.usage().inputTokens()).isEqualTo(75);
        assertThat(tracker.usage().outputTokens()).isEqualTo(12);
    }

    @Test
    void doesNotTreatAnthropicMessageStartUsageAsFinal() {
        StreamUsageTracker tracker = tracker(ProviderProtocol.ANTHROPIC);
        tracker.accept(("data: {\"type\":\"message_start\",\"message\":{\"usage\":{"
                + "\"input_tokens\":90,\"output_tokens\":1}}}\n\n").getBytes(StandardCharsets.UTF_8));
        tracker.finish();

        assertThat(tracker.usage()).isNull();
    }

    private StreamUsageTracker tracker(ProviderProtocol protocol) {
        return new StreamUsageTracker(new ObjectMapper(), new ProviderUsageParser(), protocol);
    }
}
