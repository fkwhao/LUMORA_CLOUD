package com.lumora.cloud.common.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletUserContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TrustedUserContextFilter.class)
    TrustedUserContextFilter trustedUserContextFilter(
            @Value("${lumora.security.internal-token}") String internalToken
    ) {
        return new TrustedUserContextFilter(internalToken);
    }

    @Bean
    FilterRegistrationBean<TrustedUserContextFilter> trustedUserContextFilterRegistration(
            TrustedUserContextFilter filter
    ) {
        FilterRegistrationBean<TrustedUserContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("lumoraTrustedUserContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }
}
