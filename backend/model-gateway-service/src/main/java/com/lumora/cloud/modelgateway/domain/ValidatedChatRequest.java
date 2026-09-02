package com.lumora.cloud.modelgateway.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
public record ValidatedChatRequest(
        String modelCode,
        GatewayProtocol protocol,
        boolean stream,
        long requestedMaxOutputTokens,
        ObjectNode originalBody
) {
}
