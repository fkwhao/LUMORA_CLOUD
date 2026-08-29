package com.lumora.cloud.common.context;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.api.UserContext;
import com.lumora.cloud.api.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class FeignUserContextRelayInterceptor implements RequestInterceptor {

    private final String serviceId;
    private final String internalToken;

    public FeignUserContextRelayInterceptor(String serviceId, String internalToken) {
        this.serviceId = serviceId;
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            relay(request, template, "Authorization");
            relay(request, template, AuthHeaders.REQUEST_ID);
        }

        UserContextHolder.current().ifPresent(context -> relay(context, template));

        template.header(AuthHeaders.SERVICE_ID, serviceId);
        template.header(AuthHeaders.INTERNAL_TOKEN, internalToken);
    }

    private void relay(HttpServletRequest request, RequestTemplate template, String header) {
        String value = request.getHeader(header);
        if (StringUtils.hasText(value)) {
            template.header(header, value);
        }
    }

    private void relay(UserContext context, RequestTemplate template) {
        template.header(AuthHeaders.USER_ID, context.userId());
        template.header(AuthHeaders.SESSION_ID, context.sessionId());
        template.header(AuthHeaders.DEVICE_ID, context.deviceId());
        template.header(AuthHeaders.ROLES, String.join(",", context.roles()));
        template.header(AuthHeaders.CLIENT_TYPE, context.clientType());
        template.header(AuthHeaders.REQUEST_ID, context.requestId());
    }
}
