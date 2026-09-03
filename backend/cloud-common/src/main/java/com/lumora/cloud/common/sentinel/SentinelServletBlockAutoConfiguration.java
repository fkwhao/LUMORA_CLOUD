package com.lumora.cloud.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.lumora.cloud.api.AuthHeaders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
@AutoConfiguration
@AutoConfigureBefore(name = "com.alibaba.cloud.sentinel.SentinelWebAutoConfiguration")
@ConditionalOnClass(BlockExceptionHandler.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelServletBlockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BlockExceptionHandler.class)
    BlockExceptionHandler lumoraSentinelBlockExceptionHandler() {
        return (request, response, resourceName, error) -> {
            SentinelBlockResponse blocked = SentinelBlockResponse.from(error);
            String traceId = SentinelBlockResponse.traceId(request.getHeader(AuthHeaders.REQUEST_ID));
            response.setStatus(blocked.status().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(AuthHeaders.REQUEST_ID, traceId);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.getWriter().write(json(blocked, traceId));
        };
    }

    private String json(SentinelBlockResponse blocked, String traceId) {
        return "{\"code\":\"" + blocked.code()
                + "\",\"message\":\"" + blocked.message()
                + "\",\"traceId\":\"" + traceId + "\"}";
    }
}
