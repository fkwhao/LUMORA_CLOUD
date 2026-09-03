package com.lumora.cloud.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.lumora.cloud.catalog.mapper")
public class ModelCatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelCatalogServiceApplication.class, args);
    }
}
