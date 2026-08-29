package com.lumora.cloud.modelgateway.domain;

public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        long cacheWriteTokens
) {
    public boolean hasUsage() {
        return inputTokens > 0 || outputTokens > 0 || reasoningTokens > 0
                || cacheReadTokens > 0 || cacheWriteTokens > 0;
    }
}
