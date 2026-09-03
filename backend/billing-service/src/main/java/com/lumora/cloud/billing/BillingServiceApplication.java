package com.lumora.cloud.billing;

import com.lumora.cloud.api.catalog.CatalogClient;
import com.lumora.cloud.api.fallback.CatalogClientFallbackFactory;
import com.lumora.cloud.billing.config.PaymentProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.lumora.cloud.billing.mapper")
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
@EnableFeignClients(basePackageClasses = CatalogClient.class)
@Import(CatalogClientFallbackFactory.class)
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
