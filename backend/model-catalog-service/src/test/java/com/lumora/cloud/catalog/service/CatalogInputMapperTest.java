package com.lumora.cloud.catalog.service;

import com.lumora.cloud.catalog.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogInputMapperTest {

    private final CatalogInputMapper mapper = new CatalogInputMapper();

    @Test
    void normalizesEverySupportedProviderProtocol() {
        assertThat(mapper.protocolType(" anthropic ")).isEqualTo("ANTHROPIC");
        assertThat(mapper.protocolType("openai_compatible")).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(mapper.protocolType("responses")).isEqualTo("RESPONSES");
    }

    @Test
    void rejectsUnknownProviderProtocol() {
        assertThatThrownBy(() -> mapper.protocolType("custom"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo("UNSUPPORTED_PROVIDER_PROTOCOL");
    }
}
