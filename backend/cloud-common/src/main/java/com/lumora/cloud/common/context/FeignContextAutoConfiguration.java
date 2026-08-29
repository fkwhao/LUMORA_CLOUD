package com.lumora.cloud.common.context;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FeignContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lumoraFeignUserContextRelayInterceptor")
    RequestInterceptor lumoraFeignUserContextRelayInterceptor(
            @Value("${spring.application.name:unknown-service}") String serviceId,
            @Value("${lumora.security.internal-token}") String internalToken
    ) {
        return new FeignUserContextRelayInterceptor(serviceId, internalToken);
    }
}
