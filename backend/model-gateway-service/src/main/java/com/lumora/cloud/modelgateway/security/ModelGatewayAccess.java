package com.lumora.cloud.modelgateway.security;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.modelgateway.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

@Component
public class ModelGatewayAccess {

    private static final Pattern CLIENT_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    private static final String CLOUD_GATEWAY = "lumora-cloud-gateway";

    private final byte[] internalToken;

    public ModelGatewayAccess(@Value("${lumora.security.internal-token}") String internalToken) {
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
    }

    public GatewayRequestContext requireUser(HttpHeaders headers) {
        requireTrustedGateway(headers);
        long userId = positiveLong(headers.getFirst(AuthHeaders.USER_ID));
        String sessionId = required(headers, AuthHeaders.SESSION_ID, "AUTHENTICATION_REQUIRED", "请先登录");
        String traceId = required(headers, AuthHeaders.REQUEST_ID, "REQUEST_ID_REQUIRED", "请求缺少追踪标识");
        String clientRequestId = required(
                headers, AuthHeaders.CLIENT_REQUEST_ID, "CLIENT_REQUEST_ID_REQUIRED",
                "模型请求必须提供 X-Lumora-Client-Request-Id 幂等标识"
        );
        if (!CLIENT_REQUEST_ID.matcher(clientRequestId).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLIENT_REQUEST_ID",
                    "客户端请求标识长度应为 8-128，只能包含字母、数字、点、下划线、冒号和短横线");
        }
        return new GatewayRequestContext(
                userId,
                sessionId,
                value(headers, AuthHeaders.DEVICE_ID),
                value(headers, AuthHeaders.CLIENT_TYPE),
                traceId,
                clientRequestId
        );
    }

    public void requireAdmin(HttpHeaders headers) {
        requireTrustedGateway(headers);
        positiveLong(headers.getFirst(AuthHeaders.USER_ID));
        String roles = value(headers, AuthHeaders.ROLES);
        boolean admin = java.util.Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch("ADMIN"::equals);
        if (!admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "需要管理员权限");
        }
    }

    private void requireTrustedGateway(HttpHeaders headers) {
        String providedToken = headers.getFirst(AuthHeaders.INTERNAL_TOKEN);
        String serviceId = headers.getFirst(AuthHeaders.SERVICE_ID);
        boolean trusted = providedToken != null && MessageDigest.isEqual(
                internalToken,
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
        if (!trusted || !CLOUD_GATEWAY.equals(serviceId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TRUSTED_GATEWAY_REQUIRED", "请求未经过可信云端网关");
        }
    }

    private long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
    }

    private String required(HttpHeaders headers, String name, String code, String message) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    private String value(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        return value == null ? "" : value.trim();
    }
}
