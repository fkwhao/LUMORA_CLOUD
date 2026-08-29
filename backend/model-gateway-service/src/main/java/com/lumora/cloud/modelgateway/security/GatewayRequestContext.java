package com.lumora.cloud.modelgateway.security;

public record GatewayRequestContext(
        long userId,
        String sessionId,
        String deviceId,
        String clientType,
        String traceId,
        String clientRequestId
) {
}
