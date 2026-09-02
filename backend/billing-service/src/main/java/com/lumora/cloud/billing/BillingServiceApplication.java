package com.lumora.cloud.billing;

import com.lumora.cloud.billing.config.PaymentProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.lumora.cloud.api.catalog.CatalogClient;

@SpringBootApplication
@MapperScan("com.lumora.cloud.billing.persistence.mapper")
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
@EnableFeignClients(basePackageClasses = CatalogClient.class)
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
