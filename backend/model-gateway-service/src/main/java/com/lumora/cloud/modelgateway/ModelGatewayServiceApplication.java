package com.lumora.cloud.modelgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ModelGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelGatewayServiceApplication.class, args);
    }
}
