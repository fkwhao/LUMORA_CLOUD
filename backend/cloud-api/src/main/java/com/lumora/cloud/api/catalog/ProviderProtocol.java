package com.lumora.cloud.api.catalog;

import java.util.Locale;

/**
 * Provider wire protocols supported by the Lumora model gateway.
 */
public enum ProviderProtocol {
    ANTHROPIC,
    OPENAI_COMPATIBLE,
    RESPONSES;

    public static ProviderProtocol parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provider protocol cannot be blank");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
