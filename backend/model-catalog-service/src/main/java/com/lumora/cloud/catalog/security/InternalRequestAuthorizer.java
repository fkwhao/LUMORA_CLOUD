package com.lumora.cloud.catalog.security;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.catalog.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class InternalRequestAuthorizer {

    private static final Set<String> ALLOWED_SERVICES = Set.of("lumora-model-gateway-service");

    private final byte[] expectedToken;

    public InternalRequestAuthorizer(@Value("${lumora.security.internal-token}") String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    public void requireModelGateway(HttpServletRequest request) {
        String token = request.getHeader(AuthHeaders.INTERNAL_TOKEN);
        String serviceId = request.getHeader(AuthHeaders.SERVICE_ID);
        boolean trusted = token != null && MessageDigest.isEqual(
                expectedToken,
                token.getBytes(StandardCharsets.UTF_8)
        );
        if (!trusted || !ALLOWED_SERVICES.contains(serviceId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INTERNAL_ACCESS_DENIED", "内部服务身份无效");
        }
    }
}
