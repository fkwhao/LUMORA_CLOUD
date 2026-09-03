package com.lumora.cloud.modelgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumora.cloud.modelgateway.domain.enums.GatewayProtocol;
import com.lumora.cloud.modelgateway.domain.vo.invoke.ModelGatewayResponse;
import com.lumora.cloud.modelgateway.security.GatewayRequestContext;
import reactor.core.publisher.Mono;

public interface IModelGatewayService {

    Mono<ModelGatewayResponse> invoke(
            GatewayRequestContext context,
            JsonNode body,
            GatewayProtocol protocol
    );
}
