package com.lumora.cloud.common.context;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.api.UserContext;
import com.lumora.cloud.api.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class TrustedUserContextFilter extends OncePerRequestFilter {

    private final byte[] expectedInternalToken;

    public TrustedUserContextFilter(String expectedInternalToken) {
        this.expectedInternalToken = expectedInternalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (isTrustedInternalRequest(request) && StringUtils.hasText(request.getHeader(AuthHeaders.USER_ID))) {
                UserContextHolder.set(readContext(request));
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isTrustedInternalRequest(HttpServletRequest request) {
        String provided = request.getHeader(AuthHeaders.INTERNAL_TOKEN);
        return provided != null && MessageDigest.isEqual(
                expectedInternalToken,
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private UserContext readContext(HttpServletRequest request) {
        String roleHeader = request.getHeader(AuthHeaders.ROLES);
        Set<String> roles = StringUtils.hasText(roleHeader)
                ? Arrays.stream(roleHeader.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toUnmodifiableSet())
                : Set.of();
        return new UserContext(
                request.getHeader(AuthHeaders.USER_ID),
                request.getHeader(AuthHeaders.SESSION_ID),
                request.getHeader(AuthHeaders.DEVICE_ID),
                roles,
                request.getHeader(AuthHeaders.CLIENT_TYPE),
                request.getHeader(AuthHeaders.REQUEST_ID)
        );
    }
}
