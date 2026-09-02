package com.lumora.cloud.modelgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class FeignDecoderConfigurationTest {

    @Test
    void providesJacksonMessageConverterForFeignInWebFluxApplication() {
        ObjectMapper objectMapper = new ObjectMapper();

        var converters = new FeignDecoderConfiguration().feignHttpMessageConverters(objectMapper);

        assertThat(converters.getConverters())
                .singleElement()
                .isInstanceOf(MappingJackson2HttpMessageConverter.class);
        MappingJackson2HttpMessageConverter converter =
                (MappingJackson2HttpMessageConverter) converters.getConverters().getFirst();
        assertThat(converter.getObjectMapper()).isSameAs(objectMapper);
    }
}
