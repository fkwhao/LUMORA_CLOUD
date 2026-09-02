package com.lumora.cloud.modelgateway.domain;

import com.lumora.cloud.api.catalog.ProviderProtocol;

public enum GatewayProtocol {
    LUMORA_INTERNAL,
    ANTHROPIC,
    OPENAI_COMPATIBLE,
    RESPONSES;

    public boolean isInternal() {
        return this == LUMORA_INTERNAL;
    }

    public ProviderProtocol providerProtocol() {
        if (isInternal()) {
            throw new IllegalStateException("LUMORA internal protocol is not an upstream provider protocol");
        }
        return ProviderProtocol.valueOf(name());
    }
}
