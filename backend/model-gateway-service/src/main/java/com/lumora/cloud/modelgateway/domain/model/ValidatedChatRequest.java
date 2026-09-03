package com.lumora.cloud.modelgateway.domain.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
public record ValidatedChatRequest(
        String modelCode,
        GatewayProtocol protocol,
        boolean stream,
        long requestedMaxOutputTokens,
        ObjectNode originalBody
) {
}
