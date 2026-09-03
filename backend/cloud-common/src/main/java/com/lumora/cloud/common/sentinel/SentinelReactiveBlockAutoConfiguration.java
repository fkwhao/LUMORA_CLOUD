package com.lumora.cloud.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.common.ApiError;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Instant;
@AutoConfiguration
@AutoConfigureBefore(name = "com.alibaba.cloud.sentinel.SentinelWebFluxAutoConfiguration")
@ConditionalOnClass({BlockRequestHandler.class, ServerResponse.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SentinelReactiveBlockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BlockRequestHandler.class)
    BlockRequestHandler lumoraSentinelBlockRequestHandler() {
        return (exchange, error) -> {
            SentinelBlockResponse blocked = SentinelBlockResponse.from(error);
            String traceId = SentinelBlockResponse.traceId(
                    exchange.getRequest().getHeaders().getFirst(AuthHeaders.REQUEST_ID)
            );
            return ServerResponse.status(blocked.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthHeaders.REQUEST_ID, traceId)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .bodyValue(new ApiError(blocked.code(), blocked.message(), traceId, Instant.now()));
        };
    }

}
