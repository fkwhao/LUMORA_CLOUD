package com.lumora.cloud.modelgateway.domain.vo.invoke;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import reactor.core.publisher.Flux;

public record ModelGatewayResponse(
        HttpStatusCode status,
        HttpHeaders headers,
        Flux<DataBuffer> body
) {
}
