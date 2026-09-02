package com.lumora.cloud.modelgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;

/**
 * OpenFeign uses Spring MVC message converters even when the host application is WebFlux.
 * Provide the JSON converter explicitly without enabling the Servlet web stack.
 */
@Configuration(proxyBeanMethods = false)
public class FeignDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpMessageConverters.class)
    HttpMessageConverters feignHttpMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(
                false,
                List.of(new MappingJackson2HttpMessageConverter(objectMapper))
        );
    }
}
