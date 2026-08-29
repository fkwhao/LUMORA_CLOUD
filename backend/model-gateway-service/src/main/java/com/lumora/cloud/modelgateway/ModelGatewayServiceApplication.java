package com.lumora.cloud.modelgateway;

import com.lumora.cloud.api.billing.BillingClient;
import com.lumora.cloud.api.catalog.CatalogClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients(basePackageClasses = {BillingClient.class, CatalogClient.class})
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class ModelGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelGatewayServiceApplication.class, args);
    }
}
