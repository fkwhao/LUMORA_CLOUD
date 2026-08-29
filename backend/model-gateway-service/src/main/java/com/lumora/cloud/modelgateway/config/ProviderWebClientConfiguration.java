package com.lumora.cloud.modelgateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class ProviderWebClientConfiguration {

    @Bean(destroyMethod = "dispose")
    ConnectionProvider modelProviderConnectionPool(ModelGatewayProperties properties) {
        return ConnectionProvider.builder("lumora-model-providers")
                .maxConnections(properties.provider().maxConnections())
                .pendingAcquireMaxCount(properties.provider().pendingAcquireMaxCount())
                .pendingAcquireTimeout(properties.provider().pendingAcquireTimeout())
                .maxIdleTime(properties.provider().maxIdleTime())
                .evictInBackground(properties.provider().maxIdleTime())
                .build();
    }

    @Bean
    @Qualifier("providerWebClient")
    WebClient providerWebClient(
            ConnectionProvider modelProviderConnectionPool,
            ModelGatewayProperties properties
    ) {
        HttpClient httpClient = HttpClient.create(modelProviderConnectionPool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.provider().connectTimeout().toMillis()))
                .responseTimeout(properties.provider().responseTimeout())
                .compress(true);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(properties.provider().maxErrorBodyBytes()))
                .build();
    }
}
