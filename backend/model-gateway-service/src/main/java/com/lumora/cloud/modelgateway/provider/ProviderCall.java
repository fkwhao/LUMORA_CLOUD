package com.lumora.cloud.modelgateway.provider;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import reactor.core.publisher.Flux;

public record ProviderCall(
        HttpStatusCode status,
        HttpHeaders headers,
        Flux<DataBuffer> body
) {
}
