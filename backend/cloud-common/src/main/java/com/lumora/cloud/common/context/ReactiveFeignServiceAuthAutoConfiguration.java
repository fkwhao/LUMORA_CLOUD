package com.lumora.cloud.common.context;

import com.lumora.cloud.api.AuthHeaders;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveFeignServiceAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lumoraReactiveFeignServiceAuthInterceptor")
    RequestInterceptor lumoraReactiveFeignServiceAuthInterceptor(
            @Value("${spring.application.name:unknown-service}") String serviceId,
            @Value("${lumora.security.internal-token}") String internalToken
    ) {
        return template -> {
            template.header(AuthHeaders.SERVICE_ID, serviceId);
            template.header(AuthHeaders.INTERNAL_TOKEN, internalToken);
        };
    }
}
