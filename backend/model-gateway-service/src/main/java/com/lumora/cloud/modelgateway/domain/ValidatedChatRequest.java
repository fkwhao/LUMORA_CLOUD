package com.lumora.cloud.modelgateway.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.api.catalog.ProviderProtocol;

public record ValidatedChatRequest(
        String modelCode,
        ProviderProtocol protocol,
        boolean stream,
        long requestedMaxOutputTokens,
        ObjectNode originalBody
) {
}
